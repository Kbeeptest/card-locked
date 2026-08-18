package com.cardrestricted.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RestrictionPresetSettingsTest
{
    @Test
    public void balancedEnforcesItemsAndNpcsButAllowsBanking()
    {
        RestrictionPresetSettings settings =
            RestrictionPresetSettings.forPreset(RestrictionPreset.BALANCED);
        assertEquals(RestrictionMode.ENFORCE, settings.getRestrictionMode());
        assertTrue(settings.isRestrictLockedItems());
        assertTrue(settings.isRestrictNpcCombat());
        assertTrue(settings.isAllowLockedItemBanking());
    }

    @Test
    public void strictEnforcesItemsAndNpcsAndLockedBanking()
    {
        RestrictionPresetSettings settings =
            RestrictionPresetSettings.forPreset(RestrictionPreset.STRICT);
        assertEquals(RestrictionMode.ENFORCE, settings.getRestrictionMode());
        assertTrue(settings.isRestrictLockedItems());
        assertTrue(settings.isRestrictNpcCombat());
        assertFalse(settings.isAllowLockedItemBanking());
    }

    @Test
    public void collectionOnlyDoesNotApplyItemOrNpcRestrictions()
    {
        RestrictionPresetSettings settings =
            RestrictionPresetSettings.forPreset(
                RestrictionPreset.COLLECTION_ONLY);
        assertEquals(RestrictionMode.AUDIT_ONLY, settings.getRestrictionMode());
        assertFalse(settings.isRestrictLockedItems());
        assertFalse(settings.isRestrictNpcCombat());
        assertTrue(settings.isAllowLockedItemBanking());
    }

    @Test(expected = NullPointerException.class)
    public void nullPresetIsRejected()
    {
        RestrictionPresetSettings.forPreset(null);
    }
}
