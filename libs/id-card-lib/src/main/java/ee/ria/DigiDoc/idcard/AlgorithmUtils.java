// SPDX-FileCopyrightText: Estonian Information System Authority
// SPDX-License-Identifier: LGPL-2.1-or-later

package ee.ria.DigiDoc.idcard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class AlgorithmUtils {

    private static final int BINARY_SHA1_LENGTH = 20;
    private static final int BINARY_SHA224_LENGTH = 28;
    private static final int BINARY_SHA256_LENGTH = 32;
    private static final int BINARY_SHA384_LENGTH = 48;
    private static final int BINARY_SHA512_LENGTH = 64;

    private enum ALGORITHM {
        SHA_1(new byte[]{
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00, 0x04,
                0x14
        }),
        SHA_224(new byte[]{
                0x30, 0x2D, 0x30, 0x0D, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04,
                0x02, 0x04, 0x05, 0x00, 0x04, 0x1C
        }),
        SHA_256(new byte[]{
                0x30, 0x31, 0x30, 0x0D, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04,
                0x02, 0x01, 0x05, 0x00, 0x04, 0x20
        }),
        SHA_384(new byte[]{
                0x30, 0x41, 0x30, 0x0D, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04,
                0x02, 0x02, 0x05, 0x00, 0x04, 0x30
        }),
        SHA_512(new byte[]{
                0x30, 0x51, 0x30, 0x0D, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04,
                0x02, 0x03, 0x05, 0x00, 0x04, 0x40
        });

        private final byte[] padding;

        ALGORITHM(byte[] padding) {
            this.padding = padding;
        }
    }

    static byte[] addPadding(byte[] hash, boolean ellipticCurveCertificate) throws IdCardException {
        if (ellipticCurveCertificate) {
            return hash;
        }
        try (ByteArrayOutputStream toSign = new ByteArrayOutputStream()) {
            toSign.write(getAlgorithm(hash.length).padding);
            toSign.write(hash);
            return toSign.toByteArray();
        } catch (IOException e) {
            throw new IdCardException("Failed to Add padding to hash", e);
        }
    }

    private static ALGORITHM getAlgorithm(int hashLength) throws IdCardException {
        return switch (hashLength) {
            case BINARY_SHA1_LENGTH -> ALGORITHM.SHA_1;
            case BINARY_SHA224_LENGTH -> ALGORITHM.SHA_224;
            case BINARY_SHA256_LENGTH -> ALGORITHM.SHA_256;
            case BINARY_SHA384_LENGTH -> ALGORITHM.SHA_384;
            case BINARY_SHA512_LENGTH -> ALGORITHM.SHA_512;
            default -> throw new IdCardException("Unsupported Algorithm");
        };
    }
}
