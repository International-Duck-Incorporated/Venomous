package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;

public class SafeLogout extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> health = this.sgGeneral
      .add(
         new DoubleSetting.Builder().name("health").description("Disconnect at or below this health. 0 disables.")
            .defaultValue(6.0)
            .range(0.0, 36.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Double> playerRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder().name("player-range").description("Disconnect if enemy player is within this range. 0 disables.")
            .defaultValue(0.0)
            .range(0.0, 128.0)
            .sliderMax(64.0)
            .build()
      );
   private final Setting<Integer> armorDurability = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("armor-durability")
            .description("Disconnect if armor durability is at or below this percent. 0 disables.")
            .defaultValue(0)
            .range(0, 100)
            .sliderMax(50)
            .build()
      );
   private final Setting<Integer> totemCount = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("totem-count")
            .description("Disconnect if totem count is at or below this amount. -1 disables.")
            .defaultValue(-1)
            .range(-1, 10)
            .sliderMax(5)
            .build()
      );
   private final Setting<Boolean> voidCheck = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("void-check")
            .description("Disconnect if falling near the void.")
            .defaultValue(true)
            .build()
      );
   private boolean disconnecting;

   public SafeLogout() {
      super(VenomousAddon.CATEGORY, "safe-logout", "Disconnects when health, nearby players, armor, totems or void danger become unsafe.");
   }

   @Override
   public void onActivate() {
      this.disconnecting = false;
   }

   @Override
   public void onDeactivate() {
      this.disconnecting = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (!this.disconnecting && this.mc.player != null && this.mc.level != null && this.mc.getConnection() != null) {
         String reason = this.reason();
         if (reason != null) {
            this.disconnecting = true;
            this.mc.getConnection().getConnection().disconnect(Component.literal("Venomous SafeLogout: " + reason));
         }
      }
   }

   private String reason() {
      if ((Double)this.health.get() > 0.0 && this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount() <= (Double)this.health.get()) {
         return "low health";
      }

      if ((Double)this.playerRange.get() > 0.0) {
         double r2 = (Double)this.playerRange.get() * (Double)this.playerRange.get();

         for (Player p : this.mc.level.players()) {
            if (p != this.mc.player && Friends.get().shouldAttack(p) && this.mc.player.distanceToSqr(p) <= r2) {
               return "enemy nearby: " + p.getName().getString();
            }
         }
      }

      if ((Integer)this.armorDurability.get() > 0) {
         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = this.mc.player.getItemBySlot(slot);
            if (!s.isEmpty() && s.isDamageableItem() && this.percent(s) <= (Integer)this.armorDurability.get()) {
               return "low armor durability";
            }
         }
      }

      if ((Integer)this.totemCount.get() >= 0 && this.countTotems() <= (Integer)this.totemCount.get()) {
         return "low totems";
      } else {
         return this.voidCheck.get() && this.mc.player.getY() < this.mc.level.getMinY() + 8 && this.mc.player.getDeltaMovement().y < 0.0 ? "void danger" : null;
      }
   }

   private int percent(ItemStack s) {
      int max = s.getMaxDamage();
      return max <= 0 ? 100 : Math.round((max - s.getDamageValue()) * 100.0F / max);
   }

   private int countTotems() {
      int c = 0;

      for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
         if (this.mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
            c += this.mc.player.getInventory().getItem(i).getCount();
         }
      }

      return c;
   }
}
