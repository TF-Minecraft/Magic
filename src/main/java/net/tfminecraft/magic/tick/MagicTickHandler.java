package net.tfminecraft.magic.tick;

@FunctionalInterface
public interface MagicTickHandler {

    void onTick(MagicTickContext context);
}
