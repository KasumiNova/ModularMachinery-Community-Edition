package github.kasuminova.mmce.common.handler;

import github.kasuminova.mmce.common.capability.CapabilityUpgrade;
import github.kasuminova.mmce.common.capability.CapabilityUpgradeProvider;
import github.kasuminova.mmce.common.event.machine.MachineEvent;
import github.kasuminova.mmce.common.upgrade.MachineUpgrade;
import github.kasuminova.mmce.common.upgrade.registry.RegistryUpgrade;
import hellfirepvp.modularmachinery.ModularMachinery;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.helper.UpgradeEventHandlerCT;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.MiscUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

@SuppressWarnings("MethodMayBeStatic")
public class MachineEventHandler {

    @SubscribeEvent
    public void onMachineEvent(MachineEvent event) {
        TileMultiblockMachineController controller = event.getController();

        for (List<MachineUpgrade> upgrades : controller.getFoundUpgrades().values()) {
            for (final MachineUpgrade upgrade : upgrades) {
                List<UpgradeEventHandlerCT> processors = upgrade.getEventHandlers(event.getClass());
                if (processors.isEmpty()) {
                    continue;
                }

                TileUpgradeBus parentBus = upgrade.getParentBus();
                if (parentBus == null) {
                    ModularMachinery.log.warn("Found a null UpgradeBus at controller " + MiscUtils.posToString(controller.getPos()));
                    continue;
                }

                TileUpgradeBus.UpgradeBusProvider provider = parentBus.provideComponent();
                upgrade.readNBT(provider.getUpgradeCustomData(upgrade));

                for (final UpgradeEventHandlerCT handler : processors) {
                    handler.handle(event, upgrade);
                    if (event.isCanceled()) {
                        break;
                    }
                }

                provider.setUpgradeCustomData(upgrade, upgrade.writeNBT());

                if (event.isCanceled()) {
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();

        if (!RegistryUpgrade.supportsUpgrade(stack)) {
            return;
        }

        CapabilityUpgradeProvider provider = new CapabilityUpgradeProvider();
        CapabilityUpgrade upgrade = provider.getUpgrade();

        List<MachineUpgrade> upgradeList = RegistryUpgrade.getItemUpgradeList(stack);
        if (upgradeList != null) {
            upgradeList.forEach(u -> upgrade.getUpgrades().add(u.copy(stack)));
        }

        event.addCapability(CapabilityUpgrade.CAPABILITY_NAME, provider);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onItemTooltip(ItemTooltipEvent event) {
        CapabilityUpgrade upgrade = event.getItemStack().getCapability(CapabilityUpgrade.MACHINE_UPGRADE_CAPABILITY, null);
        if (upgrade == null) {
            return;
        }
        List<MachineUpgrade> upgrades = upgrade.getUpgrades();
        List<String> toolTip = event.getToolTip();
        upgrades.forEach(machineUpgrade -> {
            toolTip.add(machineUpgrade.getType().getLocalizedName());
            toolTip.addAll(machineUpgrade.getDescriptions());
        });
    }
}
