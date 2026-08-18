package com.cardrestricted.ui;

import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.collection.ProfileSetupOptions;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.session.SessionSnapshot;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class QuestManualSnapshotPersistenceTest
{
    @Test
    public void transientNonReadyRenderDoesNotEraseManualQuestSnapshot()
        throws Exception
    {
        CardRestrictedAccountPanel[] holder = new CardRestrictedAccountPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new CardRestrictedAccountPanel(
            MembersCatalogue.create(),
            new NoopSetupHandler(),
            new NoopPackActionHandler(),
            CardArtworkProvider.none()));
        CardRestrictedAccountPanel panel = holder[0];

        SwingUtilities.invokeAndWait(() -> {
            panel.completeQuestStatusRefresh(Set.of("cook_s_assistant", "dragon_slayer_i"));
            assertEquals(2, panel.questCompletionSnapshotForTesting().size());
            panel.render(SessionSnapshot.loggedOut());
            assertEquals(
                Set.of("cook_s_assistant", "dragon_slayer_i"),
                panel.questCompletionSnapshotForTesting());
            panel.completeQuestStatusRefresh(Set.of("cook_s_assistant"));
            assertEquals(
                Set.of("cook_s_assistant"),
                panel.questCompletionSnapshotForTesting());
            panel.closeAuxiliaryWindows();
        });
    }

    private static final class NoopSetupHandler implements CollectionSetupHandler
    {
        @Override public void createCollection(ProfileSetupOptions options) { }
        @Override public void disableIntegrity() { }
        @Override public void resetProfile() { }
        @Override public void exportDiagnostics() { }
    }

    private static final class NoopPackActionHandler implements PackActionHandler
    {
        @Override public void redeemStarterPack() { }
        @Override public void purchaseStandardPack() { }
        @Override public void purchaseRareHunterPack() { }
        @Override public void purchaseNoncombatNpcPack() { }
        @Override public void purchaseAttackableNpcPack() { }
        @Override public void exchangeNexusCard(Rarity rarity) { }
    }
}
