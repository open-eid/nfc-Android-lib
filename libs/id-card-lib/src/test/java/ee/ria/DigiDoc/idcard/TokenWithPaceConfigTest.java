package ee.ria.DigiDoc.idcard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pins the {@link TokenWithPaceConfig.Builder} contract: defaults, nullability,
 * and immutability of the returned allow-list.
 */
public final class TokenWithPaceConfigTest {

    @Test
    public void builder_default_allowsAllCardTypes() {
        TokenWithPaceConfig config = new TokenWithPaceConfig.Builder().build();
        assertThat(config.allowedCardTypes()).containsExactlyElementsIn(CardType.values());
    }

    @Test
    public void allowAll_containsAllCardTypes() {
        // Named factory must stay in lock-step with the Builder default; if a
        // new CardType is added, both should pick it up automatically.
        assertThat(TokenWithPaceConfig.allowAll().allowedCardTypes())
                .containsExactlyElementsIn(CardType.values());
    }

    @Test
    public void builder_allowVarargs_replacesSet() {
        TokenWithPaceConfig config = new TokenWithPaceConfig.Builder()
                .allow(CardType.ID1, CardType.THALES)
                .build();
        assertThat(config.allowedCardTypes())
                .containsExactly(CardType.ID1, CardType.THALES);
        assertThat(config.allowedCardTypes()).doesNotContain(CardType.LATVIA_IDEMIA);
    }

    @Test
    public void builder_allowSet_emptySet_isAccepted() {
        // Empty set is legal at the builder; rejection happens at create().
        TokenWithPaceConfig config = new TokenWithPaceConfig.Builder()
                .allow(EnumSet.noneOf(CardType.class))
                .build();
        assertThat(config.allowedCardTypes()).isEmpty();
    }

    @Test
    public void builder_allowSet_null_throwsNpe() {
        assertThrows(NullPointerException.class,
                () -> new TokenWithPaceConfig.Builder().allow((Set<CardType>) null));
    }

    @Test
    public void builder_allowVarargs_nullFirst_throwsNpe() {
        assertThrows(NullPointerException.class,
                () -> new TokenWithPaceConfig.Builder().allow((CardType) null));
    }

    @Test
    public void allowedCardTypes_isUnmodifiable() {
        TokenWithPaceConfig config = new TokenWithPaceConfig.Builder()
                .allow(CardType.ID1)
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> config.allowedCardTypes().add(CardType.THALES));
    }
}
