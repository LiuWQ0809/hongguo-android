package io.github.jackotom.hongguo.android;

/** Main-thread-only idle policy. Media and Activity state decide when hiding is allowed. */
final class PlayerHud {
    static final long HIDE_DELAY_MS = 4000;
    interface Scheduler { void cancel(Runnable action); void later(Runnable action, long delay); }
    interface Surface { void visible(boolean visible); }
    private final Scheduler scheduler;
    private final Surface surface;
    private boolean eligible, visible = true, held, destroyed;
    private final Runnable hide;
    PlayerHud(Scheduler scheduler, Surface surface) {
        this.scheduler = scheduler; this.surface = surface;
        hide = () -> { if (!destroyed && eligible && visible && !held) conceal(); };
    }
    boolean isVisible() { return visible; }
    void playback(boolean canHide) {
        if (destroyed || eligible == canHide) return;
        eligible = canHide;
        if (!canHide) reveal();
        else if (visible) schedule();
    }
    void interact() { if (!destroyed) { reveal(); if (eligible) schedule(); } }
    void toggle() {
        if (destroyed) return;
        if (visible) conceal();
        else interact();
    }
    void hold(boolean interacting) {
        if (destroyed) return;
        held = interacting;
        if (eligible && visible) schedule();
    }
    void suspend() { if (!destroyed) { held = false; eligible = false; reveal(); } }
    void destroy() { destroyed = true; eligible = false; scheduler.cancel(hide); }
    private void reveal() { scheduler.cancel(hide); visible = true; surface.visible(true); }
    private void conceal() { scheduler.cancel(hide); visible = false; surface.visible(false); }
    private void schedule() { scheduler.cancel(hide); if (!held) scheduler.later(hide, HIDE_DELAY_MS); }
}
