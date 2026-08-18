package com.cardrestricted.domain;

import java.util.Objects;

public final class EntityRef
{
    private final EntityType type;
    private final int id;

    public EntityRef(EntityType type, int id)
    {
        this.type = Objects.requireNonNull(type, "type");
        this.id = id;
    }

    public static EntityRef unknown()
    {
        return new EntityRef(EntityType.UNKNOWN, -1);
    }

    public EntityType getType()
    {
        return type;
    }

    public int getId()
    {
        return id;
    }

    public boolean isKnown()
    {
        return type != EntityType.UNKNOWN && id >= 0;
    }

    @Override
    public String toString()
    {
        return type.name() + ":" + id;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof EntityRef))
        {
            return false;
        }
        EntityRef that = (EntityRef) other;
        return id == that.id && type == that.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, id);
    }
}
