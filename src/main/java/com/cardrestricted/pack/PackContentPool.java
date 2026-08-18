package com.cardrestricted.pack;

import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.domain.ActionType;

public enum PackContentPool
{
    ALL
    {
        @Override
        public boolean includes(CardDefinition card)
        {
            return true;
        }
    },
    ITEMS
    {
        @Override
        public boolean includes(CardDefinition card)
        {
            return card.getCardType() == CardType.ITEM;
        }
    },
    NONCOMBAT_NPCS
    {
        @Override
        public boolean includes(CardDefinition card)
        {
            return isNoncombatNpc(card);
        }
    },
    ATTACKABLE_NPCS
    {
        @Override
        public boolean includes(CardDefinition card)
        {
            return isAttackableNpc(card);
        }
    },
    /** Compatibility alias for the current Explorer non-combat NPC pool. */
    EXPLORER
    {
        @Override
        public boolean includes(CardDefinition card)
        {
            return isNoncombatNpc(card);
        }
    },
    /** Compatibility alias for the current Adventure combat NPC pool. */
    ADVENTURE
    {
        @Override
        public boolean includes(CardDefinition card)
        {
            return isAttackableNpc(card);
        }
    };

    public abstract boolean includes(CardDefinition card);

    private static boolean isNoncombatNpc(CardDefinition card)
    {
        return card.getCardType() == CardType.NPC
            && !card.getPermissions().contains(ActionType.NPC_ATTACK);
    }

    private static boolean isAttackableNpc(CardDefinition card)
    {
        return card.getCardType() == CardType.NPC
            && card.getPermissions().contains(ActionType.NPC_ATTACK);
    }
}
