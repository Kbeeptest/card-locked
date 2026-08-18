package com.cardrestricted.betagates;

import com.cardrestricted.prototype.CoreResetVerification;
import com.cardrestricted.prototype.Phase073FoilVerification;
import com.cardrestricted.prototype.SpellbookWidgetRulesVerification;
import com.cardrestricted.prototype.StableCoreSystemsVerification;
import com.cardrestricted.ui.CardPresentationVerification;
import org.junit.Test;

/** Executes the existing main-method verifiers as part of Gradle check. */
public final class CriticalVerificationTest
{
    @Test
    public void restrictionRuntimeAndCatalogueCoverage()
    {
        CoreResetVerification.main(new String[0]);
    }

    @Test
    public void stablePersistencePackAndNexusSystems()
        throws Exception
    {
        StableCoreSystemsVerification.main(new String[0]);
    }



    @Test
    public void foilPersistenceAndProbability()
        throws Exception
    {
        Phase073FoilVerification.main(new String[0]);
    }

    @Test
    public void spellbookAndItemContextsRemainSeparate()
    {
        SpellbookWidgetRulesVerification.main(new String[0]);
    }

    @Test
    public void cardPresentationStillRendersHeadlessly()
    {
        CardPresentationVerification.main(new String[0]);
    }
}
