/*
 * Copyright 2017 - 2025 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package ee.ria.DigiDoc.idcard;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReader;

/**
 * Required configuration for {@link TokenWithPace#create(NfcSmartCardReader,
 * TokenWithPaceConfig)}. Today it carries only an allow-list of acceptable
 * card types; modelled as a config object (rather than a bare {@link Set})
 * so future knobs (e.g. logger overrides, strictness flags) can be added
 * without another factory overload.
 *
 * <p>The factory parameter is non-null by design: an integrator who genuinely
 * wants every supported card type must say so via {@link #allowAll()}, so a
 * future library release adding (say) a new country variant cannot silently
 * widen the set of cards that the integrator's app accepts.
 */
public final class TokenWithPaceConfig {

    private final Set<CardType> allowedCardTypes;

    private TokenWithPaceConfig(Set<CardType> allowedCardTypes) {
        // EnumSet copy keeps iteration cheap and contains() O(1).
        this.allowedCardTypes = allowedCardTypes.isEmpty()
                ? EnumSet.noneOf(CardType.class)
                : EnumSet.copyOf(allowedCardTypes);
    }

    /**
     * Convenience for integrators that genuinely want every supported card
     * type. Equivalent to {@code new Builder().build()}; the named factory
     * documents intent at the call site.
     */
    public static TokenWithPaceConfig allowAll() {
        return new Builder().build();
    }

    /** Card types this configuration accepts. Unmodifiable. */
    public Set<CardType> allowedCardTypes() {
        return Collections.unmodifiableSet(allowedCardTypes);
    }

    public static final class Builder {

        // Null = default (allow all). Distinct from an explicitly-empty
        // set, which is treated as "reject everything" by create().
        private EnumSet<CardType> allowedCardTypes;

        /** Restrict to the listed card types. Replaces any previous value. */
        public Builder allow(CardType first, CardType... rest) {
            Objects.requireNonNull(first, "first");
            this.allowedCardTypes = EnumSet.of(first, rest);
            return this;
        }

        /** Restrict to the given set. Empty set rejects every card. */
        public Builder allow(Set<CardType> types) {
            Objects.requireNonNull(types, "types");
            this.allowedCardTypes = types.isEmpty()
                    ? EnumSet.noneOf(CardType.class)
                    : EnumSet.copyOf(types);
            return this;
        }

        public TokenWithPaceConfig build() {
            Set<CardType> effective = allowedCardTypes != null
                    ? allowedCardTypes
                    : EnumSet.allOf(CardType.class);
            return new TokenWithPaceConfig(effective);
        }
    }
}
