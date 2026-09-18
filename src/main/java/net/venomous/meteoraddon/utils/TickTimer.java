package net.venomous.meteoraddon.utils;

public class TickTimer {
   private int ticks = 0;

   public void tick() {
      if (this.ticks > 0) {
         this.ticks--;
      }
   }

   public boolean ready() {
      return this.ticks <= 0;
   }

   public boolean readyAndReset(int delayTicks) {
      if (!this.ready()) {
         return false;
      }

      this.reset(delayTicks);
      return true;
   }

   public void reset(int delayTicks) {
      this.ticks = Math.max(delayTicks, 0);
   }

   public void clear() {
      this.ticks = 0;
   }

   public int getTicksLeft() {
      return this.ticks;
   }
}
