package com.cardrestricted.catalog;

import com.cardrestricted.domain.EntityType;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class CatalogueValidator
{
    private static final Pattern STABLE_ID = Pattern.compile(
        "[a-z0-9]+(?:[._-][a-z0-9]+)*");

    public void validate(CardCatalogue catalogue)
    {
        if (catalogue.getFamilies().isEmpty())
        {
            throw new CatalogueValidationException(
                "The catalogue must contain at least one entity family.");
        }
        if (catalogue.getCards().isEmpty())
        {
            throw new CatalogueValidationException(
                "The catalogue must contain at least one card.");
        }

        Map<String, String> entityOwner = new HashMap<>();
        for (EntityFamily family : catalogue.getFamilies())
        {
            requireStableId(family.getFamilyId(), "family");
            if (catalogue.getContentBoundary()
                == ContentBoundary.F2P_VERTICAL_SLICE
                && !family.isFreeToPlay())
            {
                throw new CatalogueValidationException(
                    "Members family " + family.getFamilyId()
                        + " is outside the F2P vertical slice.");
            }
            if (family.getFamilyVersion() > catalogue.getCatalogueVersion())
            {
                throw new CatalogueValidationException(
                    "Family " + family.getFamilyId()
                        + " was introduced after the catalogue version.");
            }

            for (int entityId : family.allEntityIds())
            {
                String key = family.getEntityType() + ":" + entityId;
                String previous = entityOwner.put(key, family.getFamilyId());
                if (previous != null)
                {
                    throw new CatalogueValidationException(
                        "Entity " + key + " belongs to both " + previous
                            + " and " + family.getFamilyId() + ".");
                }
            }
        }

        for (CardDefinition card : catalogue.getCards())
        {
            requireStableId(card.getCardId(), "card");
            EntityFamily family;
            try
            {
                family = catalogue.requireFamily(card.getEntityFamilyId());
            }
            catch (IllegalArgumentException exception)
            {
                throw new CatalogueValidationException(
                    "Card " + card.getCardId()
                        + " references a missing entity family.");
            }

            EntityType expectedType = card.getCardType() == CardType.ITEM
                ? EntityType.ITEM
                : EntityType.NPC;
            if (family.getEntityType() != expectedType)
            {
                throw new CatalogueValidationException(
                    "Card " + card.getCardId()
                        + " does not match its entity family type.");
            }
            if (catalogue.getContentBoundary()
                == ContentBoundary.F2P_VERTICAL_SLICE
                && !card.isFreeToPlay())
            {
                throw new CatalogueValidationException(
                    "Members card " + card.getCardId()
                        + " is outside the F2P vertical slice.");
            }
            if (card.getCatalogueVersionIntroduced()
                > catalogue.getCatalogueVersion())
            {
                throw new CatalogueValidationException(
                    "Card " + card.getCardId()
                        + " was introduced after the catalogue version.");
            }

            for (CardPermissionGrant grant
                : card.getAdditionalPermissionGrants())
            {
                EntityFamily grantedFamily;
                try
                {
                    grantedFamily = catalogue.requireFamily(
                        grant.getEntityFamilyId());
                }
                catch (IllegalArgumentException exception)
                {
                    throw new CatalogueValidationException(
                        "Card " + card.getCardId()
                            + " grants a missing entity family.");
                }
                if (grantedFamily.getFamilyVersion()
                    > catalogue.getCatalogueVersion())
                {
                    throw new CatalogueValidationException(
                        "Card " + card.getCardId()
                            + " grants a family introduced after "
                            + "the catalogue version.");
                }
            }
        }
    }

    private void requireStableId(String value, String kind)
    {
        if (!STABLE_ID.matcher(value).matches())
        {
            throw new CatalogueValidationException(
                "Invalid stable " + kind + " ID " + value + ".");
        }
    }
}
