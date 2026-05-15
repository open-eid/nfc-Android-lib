# Latvian eID — IDEMIA card protocol specification

A self-contained, platform-neutral specification of the Latvian eID card
(IDEMIA ID-One Cosmo v8 / IAS-ECC, Oberthur AWP applet) sufficient to
re-implement support on any host that can speak ISO/IEC 7816-4 APDUs over
ISO/IEC 14443-4 (NfcA).

> Conventions
>
> - Bytes are hex, MSB-first, separated by spaces. `<x>` denotes a placeholder.
> - APDUs are written as `CLA INS P1 P2 [Lc data] [Le]`.
>   `Lc` and `Le` are written as a single byte where shown; APDUs without `Lc`
>   omit the data block, APDUs without `Le` omit the trailing byte.
> - "MAIN AID", "Oberthur AID", "QSCD AID" are the three applet contexts of
>   §3. "Selecting" an AID means sending the SELECT-AID APDU below.

---

## 1. Overview

|                  |                                                                                                                                                        |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Card platform    | IDEMIA ID-One Cosmo v8 (IAS-ECC)                                                                                                                       |
| Applets          | MAIN (IAS-ECC; certs, EF.CardAccess, EF 0x5001) + Oberthur AWP (auth/decrypt) + QSCD (signing). See §3 for AID values.                                 |
| Transport        | ISO 14443-4 / NfcA, ISO 7816-4 APDUs                                                                                                                   |
| PACE curve       | Negotiated from EF.CardAccess. Observed: `brainpoolP256r1` on LV, `secp256r1` on EE — both 256-bit.                                                    |
| User-key curve   | `secp384r1` per the auth/sign certificates (independent of the PACE curve). All ECDSA signatures returned by the card are 96 bytes (`r ‖ s`, 48 + 48). |
| Secure messaging | PACE-derived AES-256 CBC + AES-256-CMAC (mechanism `id-PACE-ECDH-GM-AES-CBC-CMAC-256`)                                                                 |
| User secrets     | PIN1 (auth), PIN2 (sign), PUK (unblock); CAN for PACE                                                                                                  |

Every sensitive operation (cert read with SM, PIN verify, key operations,
EF.5001 read) requires a PACE channel. The CAN is printed on the card.

---

## 2. Card identification

Match against the contactless historical bytes / ATS. The library currently
recognises two LV variants:

| Variant  | Bytes                                 |
| -------- | ------------------------------------- |
| Newer LV | `00 12 42 8F 53 65 49 44 0F 90 00`    |
| Older LV | `00 12 42 8F 54 65 49 44 32 0F 90 00` |

The leading `00 12 42 8F` is the manufacturer signature unique to LV.
Match the full byte string. (Estonian IDEMIA cards use `00 12 23 3F`
— provided here for context only; do not match LV cards against the EE
prefix.)

The byte strings above are **the historical bytes from the card's ATS**
(ISO 14443-4). The trailing `90 00` is part of the historical bytes the
card itself emits — chosen by the card OS, not a synthesized SW. Match
against the historical-bytes substring your stack exposes; if your
transport hands you a full or synthesized ATR instead, extract the
historical-bytes block first.

For reference, what some common stacks expose by default:

| Stack                        | Call                            | What it returns                                                                                                     |
| ---------------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Android (NfcA `IsoDep`)      | `getHistoricalBytes()`          | Historical bytes — exactly the strings above.                                                                       |
| Android (NfcA `IsoDep`, alt) | `getHiLayerResponse()`          | Full ATS framing — strip leading bytes to recover.                                                                  |
| iOS (CoreNFC)                | `NFCISO7816Tag.historicalBytes` | Historical bytes — same form as Android.                                                                            |
| PC/SC contactless            | `SCardStatus()` ATR             | Reader-synthesized contact-style ATR (e.g. `3B 8x 80 01 <historical> <TCK>`); parse out the historical-bytes block. |

---

## 3. Applets and AIDs

| Applet         | AID                                                                      | Purpose                                     |
| -------------- | ------------------------------------------------------------------------ | ------------------------------------------- |
| MAIN (IAS-ECC) | `A0 00 00 00 77 01 08 00 07 00 00 FE 00 00 01 00`                        | EF.CardAccess, EF 0x5001, certificate files |
| Oberthur AWP   | `E8 28 BD 08 0F F2 50 4F 54 20 41 57 50`                                 | PIN1 verify, auth, decrypt                  |
| QSCD           | `51 53 43 44 20 41 70 70 6C 69 63 61 74 69 6F 6E` (`"QSCD Application"`) | PIN2 verify, signing                        |

**SELECT AID** (always P2 = `0x0C` over NFC — `0x00` does not work):

```
00 A4 04 0C <Lc> <AID-bytes>
```

Examples:

```
SELECT MAIN:     00 A4 04 0C 10 A0 00 00 00 77 01 08 00 07 00 00 FE 00 00 01 00
SELECT Oberthur: 00 A4 04 0C 0D E8 28 BD 08 0F F2 50 4F 54 20 41 57 50
SELECT QSCD:     00 A4 04 0C 10 51 53 43 44 20 41 70 70 6C 69 63 61 74 69 6F 6E
```

Expected SW: `90 00`. Selecting an AID resets PIN security state for that
context — verify PINs **after** selecting the relevant AID.

---

## 4. PACE handshake (mandatory)

The card uses the `id-PACE-ECDH-GM-AES-CBC-CMAC-256` mechanism. OID bytes:

```
04 00 7F 00 07 02 02 04 02 04
```

The trailing `04` arc selects the **AES-256** variant of this OID family.
The `02` and `03` arcs would be the AES-128 and AES-192 variants
respectively — picking either of those would yield a non-working client
against this card.

AES-256 keys (32 bytes), 16-byte AES block size. The CAN is the PACE
password.

The whole handshake runs in plaintext. After §4.6 succeeds, secure
messaging (§5) is active and the channel is bound to the new keys.

### 4.1 Read PACE parameters from EF.CardAccess

```
SELECT EF.CardAccess:    00 A4 02 0C 02 01 1C
READ BINARY (chunked):   00 B0 <hi> <lo> 00       (loop, see §6)
```

Parse the returned ASN.1 (`SET OF SecurityInfo`, possibly without an outer
SET wrapper). Find the `PACEInfo` entry — the `SEQUENCE` whose first child
is an OID equal to **`04 00 7F 00 07 02 02 04 02 04`** (the
`id-PACE-ECDH-GM-AES-CBC-CMAC-256` mechanism this spec uses). Take its
third child (`INTEGER parameterId`) as a single byte:

> **Do not just pick the first `SEQUENCE { OID, INTEGER, INTEGER }`** —
> EF.CardAccess can carry other `SecurityInfo` entries with the same shape
> (e.g. `ChipAuthenticationInfo`), and selecting the wrong one yields a
> domain-parameter id that doesn't match the curve negotiated in §4.2.

> **BER length forms.** EF.CardAccess uses standard BER-TLV. Lengths can
> be short-form (one byte, `≤ 0x7F` = the length itself) or long-form
> (`0x81 <len>` for one length byte, `0x82 <hi> <lo>` for two). The
> reference TLV walker handles short-form, `0x81`, and `0x82`; longer
> forms aren't produced for files of this size. A minimal porter walker
> needs at least these three branches.

| `parameterId` | Curve             | Reference impl      |
| ------------- | ----------------- | ------------------- |
| `0x0C`        | `secp256r1`       | supported           |
| `0x0D`        | `brainpoolP256r1` | supported           |
| `0x0F`        | `secp384r1`       | rejected — see note |
| `0x10`        | `brainpoolP384r1` | rejected — see note |

**Currently observed on LV cards:** `0x0D` (`brainpoolP256r1`) only;
on EE cards `0x0C` (`secp256r1`).

The 384-bit rows are reference values from the IDEMIA / IAS-ECC family
and are included so a porter encountering a 384-bit variant can look up
the curve. **The reference implementation in this repo aborts with
"Unsupported PACE domain parameter" if a 384-bit parameterId is read
from EF.CardAccess.** The on-wire BER prefixes in §4.4–§4.6 are
hard-coded for 65-byte points (and the CMAC input for 79-byte length);
a 384-bit port must derive those from the curve's coordinate byte size
(see the per-step length notes below).

If no usable `parameterId` is present, abort. The remainder of this spec
assumes 256-bit curve byte sizes (65-byte uncompressed SEC1 points,
32-byte shared-secret X) — applicable to both `secp256r1` (EE) and
`brainpoolP256r1` (LV). For 384-bit curves, points are 97 bytes and X is
48 bytes; adjust the point and integer lengths shown in §4.4–§4.6
accordingly.

### 4.2 MSE SET AT (Authentication Template)

```
00 22 C1 A4 12
   80 0A 04 00 7F 00 07 02 02 04 02 04          ← PACE OID
   83 01 02                                     ← password ref = CAN
   84 01 <paramId>                              ← curve from §4.1
00
```

- SW `90 00` → continue.
- SW `63 00` → wrong CAN. Surface this distinctly to the caller (e.g.
  a dedicated exception type or error code) so a UI can prompt for
  re-entry without retrying PACE blindly.

### 4.3 GA — Get Encrypted Nonce

```
10 86 00 00 02 7C 00 00       ← CLA = 10 (chaining)
```

Response: `7C 22 80 20 <encrypted-nonce 32 bytes> 90 00`.

Decrypt the nonce with an AES-256 key derived from the CAN:

```
nonceKey   = SHA-256( CAN-bytes-utf8 || 00 00 00 03 )    ← full 32-byte digest
nonce      = AES-CBC-Decrypt(nonceKey, IV = 16×0x00, encryptedNonce)
```

(`0x03` is the constant for "nonce decryption key" in the PACE KDF — see
also `0x01` for K_enc and `0x02` for K_mac in §4.5. The full SHA-256 output
is used as the AES-256 key — do not truncate to 16 bytes.)

> **Deviation from BSI TR-03110.** The standard PACE-GM nonce `s` is one
> AES block — 16 bytes — regardless of key size, and the encrypted-nonce
> response is therefore 16 bytes. This card returns a **two-block**
> (32-byte) ciphertext, decrypts to 32 bytes of plaintext, and §4.4 uses
> the entire 32-byte plaintext as the integer scalar. A strict BSI
> implementation that hard-codes a 16-byte nonce assumption (truncating
> ciphertext or scalar) will compute a different mapped base point than
> the card and the handshake will fail at GA Mutual Authentication.
> Use the full 32-byte block on this card.

### 4.4 GA — Map Nonce

All EC points in §4.4–§4.6 use **SEC1 uncompressed encoding**:
`04 || X || Y`, where X and Y are each fixed-width big-endian integers
of the curve's coordinate byte size (32 bytes for 256-bit curves, 48 for
384-bit). Decoders should validate the leading `0x04` byte; PACE-GM
mandates uncompressed form, so other SEC1 prefixes (`02`/`03`
compressed, `00` infinity) should be rejected.

Generate ephemeral keypair `(d_term, Q_term = d_term · G)` on the curve.
The scalar must be drawn uniformly from `[1, n−1]` where `n` is the
curve order — most ECC libraries provide a helper for this.

```
10 86 00 00 45 7C 43 81 41 <Q_term-65 bytes> 00
```

Response: `7C 43 82 41 <Q_card-65 bytes> 90 00`.

> Lengths shown (`Lc=0x45`, point=65B) are for the 256-bit curves
> currently observed on LV cards. For a 384-bit PACE curve, points are
> **97 bytes** (uncompressed `04 || X || Y`, two 48-byte coordinates) and
> the inner length tag becomes `0x61` (97), the outer Lc `0x65` (101 =
> 4 + 97). Always derive point size from the curve negotiated in §4.1.

Compute the mapped base point. `nonce` is the §4.3 plaintext bytes
interpreted as a big-endian integer (the full decrypted block — 32 bytes
for the AES-256 CBC-CMAC variant). All operations are on the EC group:
`·` is scalar multiplication, `+` is **elliptic-curve point addition**
(not coordinate-wise integer addition).

> The 32-byte nonce, taken as an integer, can in principle exceed the
> curve order `n`. PACE-GM does not require explicit reduction, and
> mainstream EC libraries reduce internally during scalar multiplication
> — `nonce · G` produces the correct point either way. No explicit
> mod-`n` step is needed unless your library rejects oversized scalars.

```
H  = d_term · Q_card                    (GM mapping point — BSI calls this H)
G' = nonce · G  +  H                    (point addition)
```

`H` is **not** the session shared secret — that's `K` in §4.5. `H` is
the intermediate Generic-Mapping point that re-bases the curve before
the second key exchange. Calling both "shared secret" leads to confusion
and bugs; keep them named distinctly when porting.

### 4.5 GA — Key Agreement

Generate a second ephemeral keypair on the **mapped** base point:

```
d_term2  = random scalar in [1, n−1]
Q_term2  = d_term2 · G'

10 86 00 00 45 7C 43 83 41 <Q_term2-65 bytes> 00
```

Response: `7C 43 84 41 <Q_card2-65 bytes> 90 00`.

Compute the shared session secret and derive AES-256 session keys.
`K` is the affine X coordinate of the resulting point, encoded as a
**fixed-width big-endian integer of the curve's field byte size**:

| Curve             | Field byte size | `K` length |
| ----------------- | --------------- | ---------- |
| `secp256r1`       | 32              | 32 B       |
| `brainpoolP256r1` | 32              | 32 B       |
| `secp384r1`       | 48              | 48 B       |
| `brainpoolP384r1` | 48              | 48 B       |

Left-pad with zeros if the X integer is shorter than the field size
(rare on uniform inputs but possible). The SHA-256 input length is
therefore `coordSize + 4`.

> For all four curves listed above the field size and the group-order
> size happen to coincide. **Don't generalise that** to other curves —
> on some curves (e.g. some Koblitz / `sect` curves) field size and
> order size differ, and PACE-GM specifies the _field_ size for `K`.

```
K       = ( d_term2 · Q_card2 ).x         ← fixed-width affine X, big-endian
K_enc   = SHA-256( K || 00 00 00 01 )    ← full 32-byte digest, AES-256 key
K_mac   = SHA-256( K || 00 00 00 02 )    ← full 32-byte digest, AES-256 key
```

### 4.6 GA — Mutual Authentication

Build the auth-token data. The two length bytes track the curve's
point size:

| Curve family                             | `7F 49` length | `86` length | `Q` size |
| ---------------------------------------- | -------------- | ----------- | -------- |
| 256-bit (`secp256r1`, `brainpoolP256r1`) | `4F` (79)      | `41` (65)   | 65 B     |
| 384-bit (`secp384r1`, `brainpoolP384r1`) | `6F` (111)     | `61` (97)   | 97 B     |

Both lengths fit in BER short-form for these curves.

```
authData = 7F 49 <inner-len>                 ← public-key info template
           06 0A 04 00 7F 00 07 02 02 04 02 04   ← PACE OID
           86 <Q-len> <Q>                    ← peer's Q from §4.5
T_term   = AES-CMAC(K_mac, authData[with peer's Q = Q_card2])[0..7]
```

Bytes shown in the literal example below are the 256-bit form
(`7F 49 4F … 86 41 …`); substitute the 384-bit lengths if working with
P-384 or brainpoolP384r1.

Send:

```
00 86 00 00 0C 7C 0A 85 08 <T_term-8 bytes> 00       ← CLA = 00, no chaining
```

Response: `7C 0A 86 08 <T_card-8 bytes> 90 00`.

Verify:

```
T_card_expected = AES-CMAC(K_mac, authData[with peer's Q = Q_term2])[0..7]
must equal T_card returned by the card.
```

If the MAC matches, the PACE channel is established. From this point on
**every** APDU must be wrapped per §5.

### 4.7 Send-Sequence-Counter initialisation

```
SSC = 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00     (16 bytes, all zero)
```

The first SM-protected C-APDU increments this to `…0001` before MAC.

---

## 5. Secure messaging

After §4 every C-APDU must be wrapped and every R-APDU must be unwrapped.
The CLA byte's bits 4–5 are set (`CLA' = CLA | 0x0C`). Confidentiality:
AES-256-CBC. Integrity: AES-256-CMAC truncated to 8 bytes.

### 5.1 SSC handling

The SSC is a 16-byte big-endian integer: byte 0 is the most-significant
byte, byte 15 is the least-significant byte. To increment, start at
**index 15** and propagate the carry **toward index 0** — e.g.
`00 …00 FF` becomes `00 …01 00`, and `FF FF …FF` would wrap to all-zero
(which never happens in practice).

The counter is incremented **twice per command/response pair**:

1. Increment **before** computing the C-APDU's MAC.
2. Increment again **before** verifying the R-APDU's MAC.

### 5.2 IV for AES-CBC

Each command and each response uses an IV derived from the _current_ SSC:

```
IV = AES-ECB-Encrypt(K_enc, SSC)            ← 16-byte single-block output
```

(In the reference implementation the IV is computed by AES-ECB-encrypting
the SSC and truncating to 16 bytes — equivalent to a single-block ECB
encryption.)

### 5.3 C-APDU wrapping

Given a plaintext APDU `CLA INS P1 P2 [data] [Le]`:

1. **Masked header (4 bytes, on-wire form)**:

   ```
   maskedHeader = (CLA | 0x0C) || INS || P1 || P2
   ```

   These 4 bytes go into the wrapped APDU unchanged. A _padded_ 16-byte
   form is computed separately for MAC input in step 4 — it never appears
   on the wire.

2. **Encrypted-data DO** (only if `data` is non-empty):

   ```
   plain     = data || 80 00 …               (pad to the next multiple of 16:
                                              always append at least one 0x80
                                              byte plus zeros — a 16-byte input
                                              becomes 32 bytes, not 16)
   ct        = AES-CBC-Encrypt(K_enc, IV from §5.2, plain)

   if INS is even (data is sent to the card — every command on this
   card; the off-by-one warning below applies here):
       DO_data = 87 <Lc'> 01 ct
            (Lc' = len(ct) + 1; the 0x01 indicator is counted in Lc'.
             Example: 16-byte ct → Lc' = 0x11 (17);
                      64-byte ct → Lc' = 0x41 (65).
             Off-by-one here is a classic porter mistake.)

   if INS is odd (unreachable on this card; included for completeness):
       DO_data = 85 <Lc'> ct                 (Lc' = len(ct), no indicator)
   ```

   The reference implementation emits a **single-byte** length here. BER
   long-form (`81 <len>`, `82 <hi> <lo>`, …) is permitted by the standard
   but is not produced by the current code; current payloads (PINs, 48-byte
   hash inputs, ~65-byte ECC ciphertexts) all stay under 127 bytes after
   SM padding and don't need it. Extend before sending larger plaintexts.

   > In this protocol every wrapped C-APDU sends data with even INS
   > (`A4`, `20`, `B0`, `22`, `88`, `2A`, `24`, `2C`, `CB`), so `DO_data`
   > is always tag `87`. The `85` branch is included for completeness and
   > would be reached only if a future operation used an odd-INS write.

3. **Le DO** (only if `Le` is present):

   ```
   DO_Le = 97 01 <Le>
   ```

4. **MAC**:

   ```
   macInput = SSC || pad(maskedHeader, 16) || DO_data || DO_Le
   if (length not a multiple of 16) macInput = pad(macInput, 16)   ← conditional
   MAC      = AES-CMAC(K_mac, macInput)[0..7]
   DO_MAC   = 8E 08 MAC
   ```

   Where `pad(x, 16)` = append `0x80`, then `0x00` bytes until length is a
   multiple of 16. The 4-byte `maskedHeader` is therefore always padded
   into a 16-byte block; the final `pad` call after appending DOs is
   _only_ applied if the running length isn't already aligned.

   > **Worked example — VERIFY PIN1.** Plaintext APDU `00 20 00 01` with
   > 12-byte padded PIN, no Le.
   >
   > ```
   >   maskedHeader (4)               = 0C 20 00 01
   >   pad(maskedHeader, 16) (16)     = 0C 20 00 01 80 00 00 00 00 00 00 00 00 00 00 00
   >   plain (12)                     = <pin || FF FF FF…>
   >   plain padded (16)              = <pin || FF…> || 80               ← 0x80 added
   >   ct (16)                        = AES-CBC-Encrypt(K_enc, IV, plain padded)
   >   DO_data (19)                   = 87 11 01 <ct-16>                 ← Lc' = 0x11
   >   DO_Le (0)                      = (absent — no inner Le)
   >
   >   SSC || paddedHeader || DO_data || DO_Le
   >     = 16 + 16 + 19 + 0           = 51 bytes        ← 51 mod 16 = 3, not aligned
   >   final pad → 64 bytes (append 80 00 00 00 …)
   >   AES-CMAC(K_mac, …)[0..7]       = 8-byte MAC
   > ```
   >
   > **Observation.** For every wrapped APDU emitted by this protocol the
   > running length before the conditional final pad is always either
   > `3 mod 16` (when `DO_Le` is absent) or `6 mod 16` (when `DO_Le` is
   > present, adding 3 bytes). The `SSC + paddedHeader` prefix is always
   > 32 bytes (aligned), and the smallest `DO_data` is `87 11 01 …` = 19
   > bytes ≡ 3 mod 16 — and longer `DO_data` values stay congruent to 3
   > mod 16. So the running length is **never aligned** and the
   > conditional final pad fires every time.
   >
   > **In practice both ends always pad.** The conditional is dead code
   > on this card; treat it as "always pad". Note that ISO/IEC 7816-4
   > SM-MAC actually mandates **unconditional** padding (the `0x80`
   > terminator is part of the MAC scheme, not optional). The reference
   > implementation's conditional form is technically non-standard but
   > equivalent here because the conditional never short-circuits. A
   > clean port should just unconditionally pad both C-APDU and R-APDU
   > MAC inputs (matching the unconditional R-APDU branch in §5.4).

5. **Wrapped C-APDU**:
   ```
   (CLA|0x0C) INS P1 P2  <newLc>  DO_data  DO_Le  DO_MAC  00
   ```

   - `newLc = len(DO_data) + len(DO_Le) + len(DO_MAC)`. This is the
     **outer ISO 7816-4 Lc** (case 3 short-form) — a single byte, valid
     range 1..255. For `newLc > 255` an ISO 7816-4 **extended-length
     APDU** would be needed (`Lc` encoded as `00 <hi> <lo>`, three
     bytes — not BER long-form). IDEMIA cards over NFC commonly reject
     extended-length APDUs (see §6); in practice every wrapped C-APDU
     this card receives stays well under the 255-byte short-form limit.
   - **Le = `0x00` is appended unconditionally** after the body (request all
     remaining bytes). This trailing byte is the SM-layer Le (asks the
     card to return its SM-wrapped response) and is **independent** of
     the inner `DO_Le = 97 01 <Le>` of step 3, which carries the
     application-level Le from the plaintext APDU.

> **Scope note.** Every wrapped C-APDU emitted by this protocol either
> carries data (most APDUs) or carries an inner Le (`READ BINARY`,
> `INTERNAL AUTHENTICATE`, `PSO COMPUTE`, `PSO DECIPHER`, `GET DATA`).
> The "no data and no Le" branch is not reachable on this card — there's
> no need to special-case it. AES-CMAC over the resulting 32-byte
> aligned `SSC || pad(maskedHeader, 16)` would also work fine if it
> ever did fire.

### 5.4 R-APDU unwrapping

A protected response has the layout:

```
[ DO_85_or_87 ]  [ DO_99 status ]  DO_8E mac  90 00
```

1. If the first tag is `85` or `87`:
   - Read length byte. If high bit is set (`0x81`, `0x82`, …) read the
     additional length bytes per BER.
   - For tag `87`: skip the leading `01` indicator byte (decrement length).
   - Decrypt body with `AES-CBC-Decrypt(K_enc, IV from §5.2)`.
   - Strip ISO 7816-4 padding: scan **from the end** of the decrypted
     buffer toward the start, find the last `0x80` byte (it terminates
     the padding `80 00…00` suffix), and return everything before it.
     Scanning from the start is wrong — the plaintext can legitimately
     contain `0x80` bytes earlier in the buffer.

2. If the next tag is `99 02 90 00`, consume it. Any other status word
   inside SM should be treated as an error.

3. Tag `8E 08 <MAC>` must verify:

   ```
   SM R-APDU layout:

     ┌──────────── responsePrefix ───────────┐
     │                                       │
     [ DO_85_or_87 ……… ] [ DO_99 02 90 00 ]   [ 8E 08 <MAC-8> ]   90 00
     ←─── encrypted body ─→ ← inner status →   ←──── MAC ────→   ← outer SW
                                                                 (unprotected,
                                                                  not in MAC)

   verifyInput = SSC || responsePrefix
                 || 80 00 …                ← unconditional ISO 7816-4 pad
                                             (always at least one 0x80 byte;
                                              if the running length is already
                                              a multiple of 16, a full 16-byte
                                              80 00 … 00 block is appended)
   expectedMAC = AES-CMAC(K_mac, verifyInput)[0..7]
   ```

   The trailing `90 00` is **outside** SM and is **not** part of the
   MAC input. `responsePrefix` includes the encrypted-body DO and the
   inner DO_99 status word (when present); it does not include the
   `8E` tag, the MAC bytes, or the unprotected `90 00`.

   **Prose form (in case the box-drawing diagram doesn't render).**
   `responsePrefix` is the concatenation of the encrypted-body DO
   (`87 …` or `85 …`) and the inner DO_99 (`99 02 90 00`) if present;
   it excludes the `8E` MAC tag and value, and excludes the trailing
   unprotected `90 00`.

   (Recall: SSC has been incremented per §5.1.)

   > Note: this is **asymmetric** with the C-APDU MAC of §5.3 step 4,
   > where padding is applied only when the length is not already a
   > multiple of 16. The reference implementation pads conditionally on
   > C-APDUs and unconditionally on R-APDUs; both work in practice
   > against the LV card.

4. The trailing `90 00` outside SM is the unprotected SW.

If any of these checks fail, abort the session — the channel is no longer
trustworthy.

---

## 6. Files used by the LV card

| FID / Path      | Description                                        | SELECT                                                                                       |
| --------------- | -------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `01 1C`         | EF.CardAccess (PACE params, ASN.1)                 | `00 A4 02 0C 02 01 1C` (after MAIN AID)                                                      |
| `50 00 / 50 01` | Personal code (UTF-8)                              | `00 A4 01 0C 02 50 00`, then `00 A4 02 0C 02 50 01`                                          |
| `AD F1 34 01`   | Authentication X.509 cert                          | `00 A4 09 <P2> 04 AD F1 34 01` (after MAIN AID, P1=`09` = "select by path"; `P2` per Form below) |
| `AD F2 34 1F`   | Signing X.509 cert                                 | `00 A4 09 <P2> 04 AD F2 34 1F`                                                               |
| `50 31`         | EF.OD (PKCS#15 ObjectDirectory) — only used by §13 | `00 A4 02 0C 02 50 31`                                                                       |

### READ BINARY chunking

The LV card supports two equivalent shapes for reading a transparent EF.
Both produce identical file content; pick whichever fits your stack.

#### Form A — FCI-bounded reads (~1 round-trip faster; used by the reference iOS and Android implementations)

```
fci = transmit( 00 A4 09 04 <Lc> <path> )      ← SELECT with P2 = 04 returns the FCI
size = FCI tag 80 (or 81) value as big-endian uint
loop while bytes_read < size:
    le = min(0xE5, size - bytes_read)
    response = transmit( 00 B0 <offset_hi> <offset_lo> <le> )
    accumulate response
    offset = bytes_read
```

The card returns the ISO 7816-4 §5.3.3 FCP template, e.g.:

```
62 1E 80 02 04 9E   ← tag 62 (FCP), then tag 80 length 2 size = 0x049E = 1182 bytes
      82 01 01      ← file descriptor (transparent EF)
      83 02 34 01   ← file identifier
      88 00 8A 01 05 …
```

No `6B 00` probe at the end because the read terminates at the
FCI-declared size. **Measured on a real LV test card this saves
roughly 170–380 ms per cert read vs Form B**, mostly from skipping
the EOF probe round-trip plus a smaller per-chunk effect from the
explicit `Le`.

> If the FCI lacks tag `80`/`81`, the reader does not know how much
> to read. Either fall back to Form B, or read a sensible default
> (the reference implementation reads `0xE5 = 229` bytes which works
> for small files but silently truncates larger ones). Verbose
> logging of the FCI hex is recommended the first time you port to
> a new card variant.

#### Form B — `6B 00`-terminated reads (canonical ISO 7816-4 / spec form)

```
transmit( 00 A4 09 0C <Lc> <path> )            ← SELECT with P2 = 0C (no FCI)
loop:
    response = transmit( 00 B0 <offset_hi> <offset_lo> 00 )
    accumulate response
    offset += len(response)
until SW = 6B 00   (offset out of range — end of file)
   or SW = 6A 82   (file not found — treat as empty)
```

One extra round-trip vs Form A (the final `6B 00` probe), but no
dependency on the card's FCI implementation. Use this if you cannot
parse the FCP template, or if a card variant returns FCI without a
usable size tag.

The whole loop happens **inside** secure messaging once §4 has completed,
regardless of which form you use.

#### Notes for porters (both forms)

- Use short-form `Le` (single byte) only — do not switch to extended
  `Le = 00 00` unless you've confirmed the card accepts it. IDEMIA
  cards commonly reject extended-length APDUs over NFC.
- The card may return **fewer** bytes than `Le` requested. This is not
  an error. Always advance the offset by `response.length`, never by a
  fixed value.
- Chunk size is determined by the card's internal record size, T=CL
  frame negotiation, and SM overhead — not by your `Le`. Different
  transports yield different chunk sizes (often well below 256 bytes);
  the loop must not assume any particular value.
- **`SW1 = 0x61, SW2 = <n>` requires GET RESPONSE.** When the card
  signals `61 xx` ("`xx` more bytes available"), the read isn't finished
  — you must immediately issue an unencrypted `00 C0 00 00 <xx>` (GET
  RESPONSE), concatenate the body of that response with the body
  preceding the `61 xx`, treat the combined buffer as the (single)
  SM-wrapped response, then continue your read loop. Higher-level
  transports that wrap ISO 7816-4 (typical OS-level NFC APIs) usually
  handle this transparently and you can ignore it. Raw transports do
  not — you have to implement the GET RESPONSE branch yourself, and
  forgetting it manifests as truncated data with no obvious error.

  > **The fragments are not independently MAC'd.** On this card, an
  > SM-wrapped response that exceeds one transport frame is split at
  > the transport layer (T=CL) but the **logical SM message is a single
  > unit with one DO_8E MAC at its end**. The partial body that arrives
  > before `61 xx` does not carry its own MAC; you concatenate raw bytes
  > (not authenticated chunks), and §5.4 verifies the single MAC over
  > the reassembled whole.

- For Form B, terminate the read **only** on `6B 00` (offset past EOF)
  or `6A 82` (file not found / empty). Do not terminate on a short
  response — it just means the card chose a smaller chunk this round.
- P1/P2 encode a 16-bit offset (max `FF FF` = 65535 bytes). All LV files
  in this spec fit well within 64 KB. For files > 64 KB you'd need
  READ BINARY ODD (`INS = B1`) with an offset DO; this card has no such
  files, so a porter doesn't need to implement that variant.

---

## 7. PIN model

| Code | VERIFY P2 | Retry-counter ref | Allowed length | Notes                                                        |
| ---- | --------- | ----------------- | -------------- | ------------------------------------------------------------ |
| PIN1 | `0x01`    | `0x01`            | 4–12           | Auth + decrypt + cert ops; verify after MAIN or Oberthur AID |
| PIN2 | `0x85`    | `0x05`            | 6–12           | Signing only; verify after QSCD AID                          |
| PUK  | `0x02`    | `0x02`            | 8–12           | Unblock; verify after MAIN AID                               |

All PIN values are right-padded with `0xFF` to **12 bytes** before
transmission.

### 7.1 Verify PIN

```
00 20 00 <P2> 0C  <PIN bytes padded to 12 with 0xFF>
```

| SW      | Meaning                                                                                                                                            |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `90 00` | Success                                                                                                                                            |
| `63 C2` | Wrong PIN, 2 tries left                                                                                                                            |
| `63 C1` | Wrong PIN, 1 try left                                                                                                                              |
| `63 C0` | Wrong PIN, **this attempt exhausted the counter** — the PIN is now blocked (use PUK). Distinct from `69 83` for diagnostic logging; UX-equivalent. |
| `69 83` | PIN already blocked / authentication method blocked — no attempt was processed (counter was already at zero before this VERIFY).                   |
| `63 00` | Wrong PIN, generic / counter not reported.                                                                                                         |

### 7.2 Read retry counter

```
SELECT MAIN AID  (or QSCD AID for PIN2)
00 CB 3F FF 0A  4D 08 70 06 BF 81 <code-ref> 02 A0 80  00
```

`<code-ref>` is the _retry-counter ref_ from the table above
(`01`/`05`/`02`). The retry counter is byte **13** (0-based) of the
SM-decrypted plaintext response — i.e. read it after §5.4 unwrapping,
not from the raw on-wire bytes.

> **Firmware caveat.** The reference implementation hard-codes offset
> 13; the surrounding bytes are a BER-TLV structure that mirrors the
> request template. On a fresh port, log the full plaintext response on
> the first run, locate the retry-counter byte by hand, and confirm it
> lives at index 13 on your card before trusting this offset. Treat it
> as firmware-dependent.
>
> **Robust alternative.** Walk the TLV instead of hard-coding 13. The
> response mirrors the request template `4D 08 70 06 BF 81 <ref> …`,
> with the retry counter inside the `BF 81 <ref>` envelope. Find
> `4D` → `70` → `BF 81 <ref>` → first primitive INTEGER-shaped child
> and read that byte. This survives small layout shifts (extra optional
> fields, reordered children) that would invalidate the magic offset.

### 7.3 Change PIN

```
SELECT MAIN AID  (or QSCD AID for PIN2)
00 24 00 <verify-P2> 18  <oldPIN padded to 12 with 0xFF>  <newPIN padded to 12 with 0xFF>
```

`<verify-P2>` is the same value as the VERIFY P2 (`0x01` for PIN1, `0x85`
for PIN2, `0x02` for PUK).

### 7.4 Unblock + change PIN with PUK

```
SELECT MAIN AID                                ← PUK lives in MAIN context
VERIFY PUK                                     (§7.1, P2 = 0x02)
if changing PIN2: SELECT QSCD AID
00 2C 02 <verify-P2> 0C  <newPIN padded to 12 with 0xFF>
```

`<verify-P2>` is `0x01` for PIN1, `0x85` for PIN2.

The leading SELECT MAIN AID is required regardless of caller-side state.
Skipping it relies on the active AID happening to be MAIN, which is true
right after PACE but not after sequences that have selected QSCD in
between (e.g. reading PIN2's retry counter, which lives under QSCD).
Always select MAIN first.

---

## 8. Reading certificates

(Same flow for AUTH and SIGN; only the path differs.)

```
SELECT MAIN AID
SELECT cert by path:
    Auth:  00 A4 09 <P2> 04 AD F1 34 01
    Sign:  00 A4 09 <P2> 04 AD F2 34 1F
READ BINARY chunked (§6)
```

Both `<P2>` forms work — use `0x04` for FCI-bounded reads (§6 Form A,
~170–380 ms faster) or `0x0C` for canonical `6B 00`-terminated reads
(§6 Form B). The reference Android and iOS implementations both use
Form A. Result is the DER-encoded X.509 certificate.

---

## 9. Authentication (PIN1 → key 0x82)

```
SELECT Oberthur AID
VERIFY PIN1                          (§7.1, P2 = 0x01)
MSE SET AT:
    00 22 41 A4 06  80 01 04  84 01 82
INTERNAL AUTHENTICATE:
    00 88 00 00 <Lc> <challenge-bytes> 00
```

- `MSE SET AT` body: `80 01 04` → algorithm reference `0x04`;
  `84 01 82` → key reference `0x82`.
- The signing operation here is **INTERNAL AUTHENTICATE** (`INS 0x88`,
  ISO 7816-4 §7.5.5) — distinct from the `GENERAL AUTHENTICATE`
  (`INS 0x86`) used during the PACE handshake in §4. The card signs the
  caller-supplied `<challenge-bytes>` with the auth key and returns the
  signature directly.
- The card returns its response as a raw ECDSA signature `r || s` —
  **96 bytes** total (48 + 48 for `secp384r1`).
- **`<challenge-bytes>` is sent unmodified.** The card does **not** hash
  internally and does **not** zero-pad (unlike §10). The caller is
  responsible for the digest / format. For a Web-eID-style auth
  challenge against the `secp384r1` user keys, that's typically a
  48-byte SHA-384 hash; the card signs it as-is and returns
  `r || s` (96 bytes).

  > **Alternative.** Because the card
  > performs ECDSA on the input interpreted as a big-endian integer,
  > prepending zero bytes is mathematically inert and the card silently
  > accepts a leading-zero-padded form (e.g. a 32-byte hash front-padded
  > to 48). This is not specified and breaks the moment the operation
  > or input length changes. Pass the hash unmodified per §9 — do not
  > borrow §10's padding rule here.

> **LV vs Estonian:** Estonian IDEMIA uses a 4-byte algorithm reference
> `FF 20 08 00`; LV uses a 1-byte `0x04`. Length bytes must match
> (`80 01 04` vs `80 04 FF 20 08 00`). Do not copy Estonian code blindly.

---

## 10. Signing (PIN2 → key 0x9E)

```
SELECT QSCD AID
VERIFY PIN2                          (§7.1, P2 = 0x85)
MSE SET DST:
    00 22 41 B6 06  80 01 54  84 01 9E
PSO COMPUTE DIGITAL SIGNATURE:
    00 2A 9E 9A <Lc> <padded-hash> 00
```

- `MSE SET DST` body: algorithm `0x54`, key `0x9E`.
- **Hash padding:** PSO COMPUTE on this card expects exactly **48 bytes**
  of input. Shorter hashes (e.g. SHA-256, 32 bytes) must be **left-padded
  with zero bytes to 48 bytes** before transmission — i.e. zeros first,
  hash second:

  ```
  Input to PSO COMPUTE for SHA-256 digest H (32 bytes):
      00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00     ← 16 zero bytes
      H[0]  H[1]  …  H[31]                                 ← 32-byte digest
                                                          ───
                                                          48 bytes total
  ```

  Hashes already ≥ 48 bytes (SHA-384, or SHA-512 truncated to 48) are
  passed through unchanged. (The 48-byte requirement is a downstream
  consequence of the user signing key being `secp384r1`.)

  > **Why left-pad.** The card performs raw ECDSA on the input
  > interpreted as a big-endian integer. Leading-zero bytes are
  > mathematically inert — `0x00…00 || H` and `H` represent the same
  > integer. Right-padding (or any other extension) would change the
  > integer value and yield a different signature; left-padding with
  > zeros does not. A useful corollary: it doesn't matter if the hash
  > itself happens to start with a `0x00` byte (e.g. roughly 1 in 256
  > SHA-256 outputs do) — the resulting integer is the same.
  >
  > **Hashes longer than 48 bytes** (e.g. raw SHA-512, 64 bytes) are
  > unspecified. The reference impl passes them through; the card may
  > truncate to the first 48 bytes (per FIPS 186-4 ECDSA convention),
  > reject the input, or behave unpredictably. Not exercised on this
  > branch — truncate caller-side to 48 bytes if you need a
  > deterministic outcome.

- Response is the raw ECDSA signature `r || s` — **96 bytes** total
  (48 + 48 for `secp384r1`).

---

## 11. Decryption (PIN1 → key 0x82)

> ⚠ **Unverified path.** Included for completeness; **not exercised by
> the reference tests on this branch**. The byte-level APDU sequence
> below matches the reference codebase, but the upstream-level
> characterisation of what the card is actually doing (raw ECDH /
> ECKA — see further down) has not been verified against a known-good
> ciphertext on this branch. Verify before relying on this section.

```
SELECT Oberthur AID
VERIFY PIN1                          (§7.1, P2 = 0x01)
MSE SET CT:
    00 22 41 B8 06  80 01 04  84 01 82
PSO DECIPHER:
    00 2A 80 86 <Lc> 00 <ciphertext> 00     ← leading 0x00 is the encrypted-message indicator
```

The leading `0x00` byte before the ciphertext is required — it marks the
input as an encrypted message (not a hash).

**What this operation actually does.** `MSE SET CT (algo 0x04, key 0x82)`
followed by `PSO DECIPHER` with the leading `0x00` indicator is, on
IAS-ECC IDEMIA cards, the **raw ECDH / ECKA** primitive: the input is
an ephemeral peer SEC1 point (`04 || X || Y`, 97 bytes for `secp384r1`
user keys), and the card returns the shared-secret X coordinate. The
host then derives a content-decryption key from that secret per the
application's encryption scheme. (One concrete consumer is CDOC2, the
encrypted-container format used by the broader DigiDoc ecosystem from
which this library originates; other applications can layer arbitrary
schemes on top of the same ECKA primitive.)

The reference codebase exposes this method on its token interface but
the demo app does not exercise it. If you have no specific ciphertext
format to interoperate with, you can skip this section.

> Verify against a known good ciphertext before relying on the
> characterisation above — it matches standard IAS-ECC behaviour but is
> not exercised by the reference test path on this branch.

---

## 12. Personal data extraction

LV stores **only the personal code on-card**; everything else (name,
issuing country, document number, certificate expiry) comes from the
auth certificate's subject DN and `Validity` field.

> **Cardholder citizenship is not exposed by LV cards over NFC.** The
> cert subject's `C` RDN is the **issuing country** of the certificate
> authority (`LV` on a Latvian eID), not the cardholder's nationality —
> in practice they coincide for citizen-issued eID, but a foreign
> resident's LV-issued card would still carry `C=LV`. Treat the two as
> distinct: do not surface the cert `C` value to users under a
> "citizenship" label.

> **Certificate expiry vs document expiry.** What is read here is the
> **auth certificate's `notAfter`**, which is typically shorter than the
> physical-card validity printed on the document. LV cards do not expose
> the document expiry over NFC; if your application needs the document
> expiry it must come from a different source (visual inspection, MRZ
> read, or out-of-band data).

### 12.1 Read the personal code

```
SELECT MAIN AID
00 A4 01 0C 02 50 00         ← SELECT DF 0x5000
00 A4 02 0C 02 50 01         ← SELECT EF 0x5001
00 B0 00 00 00               ← single-shot READ BINARY (Le=0x00 = up to 256
                               bytes; the EF body is ~12 bytes — fits in one
                               APDU, no chunking needed)
```

Strip any trailing `0xFF` bytes (CardOS unused-space padding on
fixed-size EFs) **before** decoding — most string-trimming primitives
operate on whitespace only and won't remove `0xFF`. Then decode the
remaining bytes as UTF-8 and trim ASCII whitespace.

> If a future card revision puts more than ~250 bytes in EF 0x5001, the
> single-shot read above silently truncates. Switch to the §6 chunked
> loop (terminate on `6B 00`) for any file you don't have a hard
> upper-bound size for.

### 12.2 Parse the auth certificate subject DN

Read the auth certificate (§8), parse it as X.509, and extract the
following from the subject DN:

| RDN OID                            | Field                   | Notes                                                                                                   |
| ---------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------- |
| `2.5.4.4`                          | surname                 |                                                                                                         |
| `2.5.4.42`                         | givenName               |                                                                                                         |
| `2.5.4.5`                          | serialNumber            | format `PNOLV-<personalCode>`                                                                           |
| `2.5.4.6` (RFC2253 short name `C`) | issuing country         | ISO 3166-1 alpha-2 — `LV` on a Latvian eID. **Not** the cardholder's citizenship — see the note in §12. |
| `Validity.notAfter` (X.509 field)  | certificate expiry date | not in the DN. This is the auth-cert validity, not the document expiry.                                 |

The library uses the auth-cert `serialNumber` RDN value (e.g.
`PNOLV-<personalCode>`) as the document number — there is no separate
document-number field on the card.

#### Recommended: parse the X.500 name structurally

Use your X.509 library's "RDN by OID" accessor — every mainstream stack
has one — and call it for each OID in the table above. You should not
need to handle DER tag bytes, BER lengths, or RFC 2253 escape sequences
yourself; the library returns each RDN value already decoded to a
native string regardless of whether the source DER encoding was
`UTF8String`, `PrintableString`, `BMPString`, etc. (See §16 for the
concrete Java implementation used in this codebase.)

#### Fallback: parsing RFC2253 hex-encoded values

If you must work with the RFC2253 string form (e.g. from
`X500Principal.getName(RFC2253)`), non-ASCII RDN values come back as
`#`-prefixed DER hex:

```
2.5.4.4=#0C0A4B7572756B73206265726E73            ← UTF8String "Kuruks berns"
```

To decode:

1. Drop the leading `#`.
2. Hex-decode the rest.
3. The first byte is the DER tag (`0x0C` = UTF8String). The next byte is
   the length (BER short form), or `0x81 <len>` (long form with 1 length
   byte). Skip those bytes.
4. The remaining bytes are UTF-8 text.

The string form also requires care with escaped commas inside multi-valued
RDN strings, escaped backslashes, and short-name-vs-OID variation. The
parsed-X500Name path avoids all of this.

### 12.3 Date of birth from the personal code

The Latvian personal code carries DOB only in the **old format**.

Callers should pass the code in canonical form (`DDMMYY-CZZZQ`, with or
without the dash; no surrounding whitespace) — typically the trimmed
UTF-8 output of §12.1. Whitespace handling and Unicode normalisation
are out of scope for the parser.

> **Do not confuse with the Estonian personal code scheme.** Estonia's
> personal identification code uses a _gender + century_ leading digit
> (`1`/`2` → 19th c., `3`/`4` → 20th c., `5`/`6` → 21st c.). Latvia uses
> a _century-only_ digit at position 6, with no gender encoding.
> Applying the Estonian rules to a Latvian code yields wrong DOBs.
> Canonical specification is the responsibility of Latvia's PMLP
> (Pilsonības un migrācijas lietu pārvalde — Office of Citizenship and
> Migration Affairs); reference Latvian secondary law if you need to
> validate edge cases.

> **New-format detector is restrictive.** This spec checks for `32` as
> the leading two digits of the post-2017 format. If Latvia ever
> introduces additional new-format prefixes (e.g. `33…`), the parser
> will fall through to the old-format branch, fail the century-digit
> check at position 6, and return null. That's safe (no wrong DOB
> emitted) but it's not future-proof — revisit this rule when new
> formats are introduced.

1. Strip dashes from the personal code.
2. If the digit string starts with `32`, the code is post-2017 format:
   DOB is **not encoded**, return null.
3. Otherwise (old format `DDMMYY-CZZZQ`):

   | Index | Field          |
   | ----- | -------------- |
   | 0..1  | day            |
   | 2..3  | month          |
   | 4..5  | year (2-digit) |
   | 6     | century digit  |

   Century digit: `0` → 1800, `1` → 1900, `2` → 2000. Anything else is
   invalid — return null and surface a parse error in logs.

   ```
   DOB = (century + yearShort) || "-" || month || "-" || day      (ISO 8601)
   ```

---

## 13. Optional: dynamic key-reference discovery (PrKDF)

The current LV implementation hard-codes `authKeyRef = 0x82` and
`signKeyRef = 0x9E`, so this section is reference material for porters
faced with a card variant whose key slots are unknown.

> **This is a deliberately minimal walker, not a robust PKCS#15 parser.**
> It assumes a single conformant PrKDF, takes the **first** matching tag
> at every level, and ignores anything else. Treat it as a starting
> point: if you point it at a card whose PKCS#15 layout differs (multiple
> private-key entries, optional fields reordered, RSA mixed with ECC,
> etc.) it will silently return the wrong key reference. Validate
> against your target card before relying on it.

> **Per-applet PrKDF.** The auth and sign keys on this card live in
> **different applet contexts**: the auth key (`0x82`) is registered in
> the Oberthur AWP applet's PrKDF, the sign key (`0x9E`) in the QSCD
> applet's PrKDF. Each applet has its own EF.OD → PrKDF chain. That's
> why the SELECT step below is parameterised — Oberthur for auth-key
> discovery, QSCD for sign-key discovery — and why running the walk
> under the wrong AID returns the other key (or no key at all).

```
SELECT <relevant AID>      (Oberthur for auth, QSCD for sign)
00 A4 02 0C 02 50 31       ← SELECT EF.OD
READ BINARY chunked (§6)
```

Parse the EF.OD as TLVs:

1. Find a top-level `A0` (PrivateKey directory entry).
2. Inside, find a `30` (SEQUENCE).
3. Inside that, find a 2-byte `04` (OCTET STRING) — that is the PrKDF
   file id.

```
00 A4 02 0C 02 <prkdf-file-id>
READ BINARY chunked (§6)
```

Iterate the top-level SEQUENCE entries of PrKDF. For each entry, child[1]
is `CommonKeyAttributes`. Inside that SEQUENCE, find an INTEGER (`02`):

- Length 1 → byte is the key reference.
- Length 2 → bytes are `00 <ref>` (DER positive padding for refs ≥ `0x80`)
  → byte[1] is the key reference.

Use the first key ref found per AID context (auth in Oberthur, sign in
QSCD).

---

## 14. End-to-end session sketch

A complete "card-info + sign" session, with literal step ordering:

```
1.  Connect NfcA, read historical bytes; match against §2.
2.  SELECT MAIN AID                          (§3)
3.  PACE handshake using user-entered CAN    (§4)
4.  Now SM is on; every following APDU is wrapped per §5.
5.  Read auth cert: SELECT MAIN, SELECT path AD F1 34 01, READ BINARY chunks.
6.  Read sign cert: SELECT MAIN, SELECT path AD F2 34 1F, READ BINARY chunks.
7.  Read personal code: SELECT DF 5000, SELECT EF 5001, single-shot
    READ BINARY (~12 bytes); strip trailing 0xFF and decode UTF-8.
    See §12.1 — the single-shot read assumes the file fits in one
    response and would need to switch to §6 chunked READ BINARY if the
    file ever grows beyond that.
8.  (For card view) Read PIN1 retry counter (§7.2).
9.  (For sign)
       SELECT QSCD AID
       VERIFY PIN2                           (§7.1)
       MSE SET DST + PSO COMPUTE             (§10)
10. (For auth)
       SELECT Oberthur AID
       VERIFY PIN1                           (§7.1)
       MSE SET AT + INTERNAL AUTHENTICATE    (§9; INS 0x88 — not the
                                              GENERAL AUTHENTICATE 0x86
                                              used during PACE)
11. Disconnect / let the card field drop.
```

If the user re-taps after a disconnection, restart from step 1 — PACE
keys do not survive the loss of the contactless field.

---

## 15. Reference checklist for porters

Use this list to validate a fresh implementation against a physical LV
card:

- [ ] Historical bytes match exactly one entry in §2.
- [ ] PACE MSE SET AT (§4.2) returns `90 00` with the right CAN, `63 00`
      with a wrong CAN.
- [ ] **First post-PACE APDU is SM-wrapped.** The on-wire CLA byte has
      `0x0C` set, the body contains a `8E 08 …` MAC tag, and the
      response is similarly wrapped — i.e. you have not silently fallen
      through to plaintext (which IDEMIA permits for some unprotected
      files and would mask a broken SM implementation by letting cert
      reads work anyway).
- [ ] After PACE, the auth certificate (§8) reads as a valid DER X.509.
- [ ] The auth cert subject contains `2.5.4.5 = PNOLV-<personalCode>`,
      and the on-card EF 0x5001 contains the same `<personalCode>`.
- [ ] The auth cert subject `2.5.4.6 (C)` is `LV` and is treated as
      **issuing country**, not citizenship (§12).
- [ ] PIN1 retry counter (§7.2) reads in 0..3 and decrements after a wrong
      VERIFY.
- [ ] Auth (§9) returns a **96-byte** ECDSA signature (`r || s`, 48 + 48
      bytes) — the user keys are `secp384r1`, independent of whichever
      256-bit curve PACE used.
- [ ] Signing (§10) returns a **96-byte** ECDSA signature on a SHA-256
      hash that has been left-padded with zeros to 48 bytes per §10.
- [ ] Old-format personal code (e.g. `010195-12345`) parses to
      `1995-01-01`; new-format (`32xxxxxx`) yields no DOB.
- [ ] PUK + new PIN flow (§7.4) succeeds for both PIN1 and PIN2.

---

## 16. Reference implementation (Java/Android)

The protocol description above stands on its own. This section points
at one concrete implementation — the Java/Android codebase from which
the spec was derived — for cross-checking. A port to any other platform
should follow the spec proper, not these files.

Source-of-truth Java classes used to derive this spec:

| File                                                                                 | Role                                                                  |
| ------------------------------------------------------------------------------------ | --------------------------------------------------------------------- |
| `libs/id-card-lib/src/main/java/ee/ria/DigiDoc/idcard/TokenWithPace.java`            | ATR table → dispatch                                                  |
| `libs/id-card-lib/src/main/java/ee/ria/DigiDoc/idcard/Idemia.java`                   | AIDs, cert paths, PIN refs and padding, retry counter, change/unblock |
| `libs/id-card-lib/src/main/java/ee/ria/DigiDoc/idcard/IdemiaWithPace.java`           | PACE handshake, secure messaging, dynamic key-ref discovery           |
| `libs/id-card-lib/src/main/java/ee/ria/DigiDoc/idcard/LatviaIdemiaWithPace.java`     | LV-specific MSE values, key refs, personal-data flow                  |
| `libs/id-card-lib/src/main/java/ee/ria/DigiDoc/idcard/LatviaPersonalDataParser.java` | Personal-code → DOB                                                   |

If this spec and the code disagree, the code is authoritative — please open
a PR to fix the spec.
