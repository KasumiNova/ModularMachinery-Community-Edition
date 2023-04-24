/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.integration.crafttweaker;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.data.IData;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.item.IngredientStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.oredict.IOreDictEntry;
import crafttweaker.util.IEventHandler;
import github.kasuminova.mmce.common.concurrent.Action;
import hellfirepvp.modularmachinery.common.crafting.PreparedRecipe;
import hellfirepvp.modularmachinery.common.crafting.RecipeRegistry;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentRequirement;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentSelectorTag;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.event.recipe.*;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.helper.AdvancedItemModifier;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.helper.AdvancedItemNBTChecker;
import hellfirepvp.modularmachinery.common.lib.RequirementTypesMM;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineRegistry;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceType;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.*;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RecipePrimer
 * Created by HellFirePvP
 * Date: 02.01.2018 / 18:18
 */
@ZenRegister
@ZenClass("mods.modularmachinery.RecipePrimer")
public class RecipePrimer implements PreparedRecipe {

    private final ResourceLocation name, machineName;
    private final int tickTime, priority;
    private final boolean doesVoidPerTick;

    private final List<ComponentRequirement<?, ?>> components = new LinkedList<>();
    private final List<Action> needAfterInitActions = new LinkedList<>();
    private final List<String> toolTipList = new ArrayList<>();
    private final Map<Class<?>, List<IEventHandler<RecipeEvent>>> recipeEventHandlers = new HashMap<>();
    private boolean isParallelized = Config.recipeParallelizeEnabledByDefault;
    private boolean singleThread = false;
    private String threadName = "";
    private ComponentRequirement<?, ?> lastComponent = null;

    public RecipePrimer(ResourceLocation registryName, ResourceLocation owningMachine, int tickTime, int configuredPriority, boolean doesVoidPerTick) {
        this.name = registryName;
        this.machineName = owningMachine;
        this.tickTime = tickTime;
        this.priority = configuredPriority;
        this.doesVoidPerTick = doesVoidPerTick;
    }

    @ZenMethod
    public RecipePrimer setParallelizeUnaffected(boolean unaffected) {
        if (lastComponent instanceof ComponentRequirement.Parallelizable) {
            ((ComponentRequirement.Parallelizable) lastComponent).setParallelizeUnaffected(unaffected);
        } else {
            CraftTweakerAPI.logWarning("The Input / Output must can be parallelized!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setParallelized(boolean isParallelized) {
        this.isParallelized = isParallelized;
        return this;
    }

    @ZenMethod
    public RecipePrimer setChance(float chance) {
        if (lastComponent != null) {
            if (lastComponent instanceof ComponentRequirement.ChancedRequirement) {
                ((ComponentRequirement.ChancedRequirement) lastComponent).setChance(chance);
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] Cannot set chance for not-chance-based Component: " + lastComponent.getClass().toString());
            }
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setTag(String selectorTag) {
        if (lastComponent != null) {
            lastComponent.setTag(new ComponentSelectorTag(selectorTag));
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setPreViewNBT(IData nbt) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem) {
                ((RequirementItem) lastComponent).previewDisplayTag = CraftTweakerMC.getNBTCompound(nbt);
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] setNBT(String nbtString) only can be applied to Item!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] setNBT(String nbtString) only can be applied to Item!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setNBTChecker(AdvancedItemNBTChecker checker) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem) {
                ((RequirementItem) lastComponent).setNbtChecker(checker);
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] setNBTChecker(AdvancedItemNBTChecker checker) only can be applied to Item or Catalyst!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] setNBTChecker(AdvancedItemNBTChecker checker) only can be applied to Item or Catalyst!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addItemModifier(AdvancedItemModifier modifier) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem) {
                ((RequirementItem) lastComponent).addItemModifier(modifier);
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] addItemModifier(AdvancedItemModifier checker) only can be applied to Item or Catalyst!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] addItemModifier(AdvancedItemModifier checker) only can be applied to Item or Catalyst!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addRecipeTooltip(String... tooltips) {
        toolTipList.addAll(Arrays.asList(tooltips));
        return this;
    }

    @ZenMethod
    public RecipePrimer addSmartInterfaceDataInput(String typeStr, float minValue, float maxValue) {
        needAfterInitActions.add(() -> {
            DynamicMachine machine = MachineRegistry.getRegistry().getMachine(machineName);
            if (machine == null) {
                CraftTweakerAPI.logError("Could not find machine `" + machineName.toString() + "`!");
                return;
            }
            SmartInterfaceType type = machine.getSmartInterfaceType(typeStr);
            if (type == null) {
                CraftTweakerAPI.logError("SmartInterfaceType " + typeStr + " Not Found!");
                return;
            }
            appendComponent(new RequirementInterfaceNumInput(type, minValue, maxValue));
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addSmartInterfaceDataInput(String typeStr, float value) {
        return addSmartInterfaceDataInput(typeStr, value, value);
    }

    /**
     * 设置此配方在工厂中同时运行的数量是否不超过 1。
     */
    @ZenMethod
    public RecipePrimer setSingleThread(boolean singleThread) {
        this.singleThread = singleThread;
        return this;
    }

    /**
     * 设置此配方只能被指定的核心线程执行。
     * @param name 线程名
     */
    @ZenMethod
    public RecipePrimer setThreadName(String name) {
        this.threadName = name;
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // EventHandlers
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addCheckHandler(IEventHandler<RecipeCheckEvent> event) {
        addRecipeEventHandler(RecipeCheckEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addStartHandler(IEventHandler<RecipeStartEvent> event) {
        addRecipeEventHandler(RecipeStartEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addPreTickHandler(IEventHandler<RecipePreTickEvent> event) {
        addRecipeEventHandler(RecipePreTickEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addTickHandler(IEventHandler<RecipeTickEvent> event) {
        addRecipeEventHandler(RecipeTickEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFailureHandler(IEventHandler<RecipeFailureEvent> event) {
        addRecipeEventHandler(RecipeFailureEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFinishHandler(IEventHandler<RecipeFinishEvent> event) {
        addRecipeEventHandler(RecipeFinishEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryStartHandler(IEventHandler<FactoryRecipeStartEvent> event) {
        addRecipeEventHandler(FactoryRecipeStartEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryPreTickHandler(IEventHandler<FactoryRecipePreTickEvent> event) {
        addRecipeEventHandler(FactoryRecipePreTickEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryTickHandler(IEventHandler<FactoryRecipeTickEvent> event) {
        addRecipeEventHandler(FactoryRecipeTickEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryFailureHandler(IEventHandler<FactoryRecipeFailureEvent> event) {
        addRecipeEventHandler(FactoryRecipeFailureEvent.class, event);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryFinishHandler(IEventHandler<FactoryRecipeFinishEvent> event) {
        addRecipeEventHandler(FactoryRecipeFinishEvent.class, event);
        return this;
    }

    @SuppressWarnings("unchecked")
    private <H extends RecipeEvent> void addRecipeEventHandler(Class<H> hClass, IEventHandler<H> handler) {
        recipeEventHandlers.putIfAbsent(hClass, new ArrayList<>());
        recipeEventHandlers.get(hClass).add((IEventHandler<RecipeEvent>) handler);
    }

    //----------------------------------------------------------------------------------------------
    // General Input & Output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addInput(IIngredient input) {
        if (input instanceof IItemStack ||
            input instanceof IOreDictEntry ||
            input instanceof IngredientStack && input.getInternal() instanceof IOreDictEntry) {
            addItemInput(input);
        } else if (input instanceof ILiquidStack) {
            addFluidInput((ILiquidStack) input);
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid input type %s(%s)! Ignored.", input, input.getClass()));
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addInputs(IIngredient... inputs) {
        for (IIngredient input : inputs) {
            addInput(input);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addOutput(IIngredient output) {
        if (output instanceof IItemStack ||
            output instanceof IOreDictEntry ||
            output instanceof IngredientStack && output.getInternal() instanceof IOreDictEntry) {
            addItemOutput(output);
        } else if (output instanceof ILiquidStack) {
            addFluidOutput((ILiquidStack) output);
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid output type %s(%s)! Ignored.", output, output.getClass()));
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addOutputs(IIngredient... outputs) {
        for (IIngredient output : outputs) {
            addOutput(output);
        }
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // Energy input & output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addEnergyPerTickInput(long perTick) {
        requireEnergy(IOType.INPUT, perTick);
        return this;
    }

    @ZenMethod
    public RecipePrimer addEnergyPerTickOutput(long perTick) {
        requireEnergy(IOType.OUTPUT, perTick);
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // FLUID input & output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addFluidInput(ILiquidStack fluid) {
        requireFluid(IOType.INPUT, fluid, false);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidInputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidInput(fluid);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickInput(ILiquidStack fluid) {
        requireFluid(IOType.INPUT, fluid, true);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickInputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidPerTickInput(fluid);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidOutput(ILiquidStack fluid) {
        requireFluid(IOType.OUTPUT, fluid, false);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidOutputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidOutput(fluid);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickOutput(ILiquidStack fluid) {
        requireFluid(IOType.OUTPUT, fluid, true);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickOutputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidPerTickOutput(fluid);
        }
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // GAS input & output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasInput(String gasName, int amount) {
        requireGas(IOType.INPUT, gasName, amount);
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasOutput(String gasName, int amount) {
        requireGas(IOType.OUTPUT, gasName, amount);
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // ITEM input
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addItemInput(IIngredient input) {
        if (input instanceof IItemStack) {
            requireFuel(IOType.INPUT, (IItemStack) input);
        } else if (input instanceof IOreDictEntry) {
            requireFuel(IOType.INPUT, ((IOreDictEntry) input).getName(), 1);
        } else if (input instanceof IngredientStack && input.getInternal() instanceof IOreDictEntry) {
            requireFuel(IOType.INPUT, ((IOreDictEntry) input.getInternal()).getName(), input.getAmount());
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid input type %s(%s)! Ignored.", input, input.getClass()));
        }

        return this;
    }

    @Deprecated
    @ZenMethod
    public RecipePrimer addItemInput(IOreDictEntry oreDict, int amount) {
        requireFuel(IOType.INPUT, oreDict.getName(), amount);
        CraftTweakerAPI.logWarning(String.format("[ModularMachinery] Deprecated method " +
                                                 "`addItemInput(<ore:%s>, %s)`! Consider using `addItemInput(<ore:%s> * %s)`",
                oreDict.getName(), amount, oreDict.getName(), amount)
        );
        return this;
    }

    @ZenMethod
    public RecipePrimer addItemInputs(IIngredient... inputs) {
        for (IIngredient input : inputs) {
            addItemInput(input);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFuelItemInput(int requiredTotalBurnTime) {
        requireFuel(requiredTotalBurnTime);
        return this;
    }

    @ZenMethod
    public RecipePrimer addIngredientArrayInput(IngredientArrayPrimer ingredientArrayPrimer) {
        appendComponent(new RequirementIngredientArray(ingredientArrayPrimer.getIngredientStackList()));
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // ITEM output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addItemOutput(IIngredient output) {
        if (output instanceof IItemStack) {
            requireFuel(IOType.OUTPUT, (IItemStack) output);
        } else if (output instanceof IOreDictEntry) {
            requireFuel(IOType.OUTPUT, ((IOreDictEntry) output).getName(), 1);
        } else if (output instanceof IngredientStack && output.getInternal() instanceof IOreDictEntry) {
            requireFuel(IOType.OUTPUT, ((IOreDictEntry) output.getInternal()).getName(), output.getAmount());
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid output type %s(%s)! Ignored.", output, output.getClass()));
        }

        return this;
    }

    @Deprecated
    @ZenMethod
    public RecipePrimer addItemOutput(IOreDictEntry oreDict, int amount) {
        requireFuel(IOType.OUTPUT, oreDict.getName(), amount);
        CraftTweakerAPI.logWarning(String.format("[ModularMachinery] Deprecated method " +
                                                 "`addItemOutput(<ore:%s>, %s)`! Consider using `addItemOutput(<ore:%s> * %s)`",
                oreDict.getName(), amount, oreDict.getName(), amount)
        );
        return this;
    }

    @ZenMethod
    public RecipePrimer addItemOutputs(IIngredient... inputs) {
        for (IIngredient input : inputs) {
            addItemOutput(input);
        }

        return this;
    }

    //----------------------------------------------------------------------------------------------
    // Catalyst
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addCatalystInput(IIngredient input, String[] tooltips, RecipeModifier[] modifiers) {
        if (input instanceof IItemStack) {
            requireCatalyst((IItemStack) input, tooltips, modifiers);
        } else if (input instanceof IOreDictEntry) {
            requireCatalyst(((IOreDictEntry) input).getName(), 1, tooltips, modifiers);
        } else if (input instanceof IngredientStack && input.getInternal() instanceof IOreDictEntry) {
            requireCatalyst(((IOreDictEntry) input.getInternal()).getName(), input.getAmount(), tooltips, modifiers);
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid input type %s(%s)! Ignored.", input, input.getClass()));
        }

        return this;
    }

    //----------------------------------------------------------------------------------------------
    // Internals
    //----------------------------------------------------------------------------------------------
    private void requireEnergy(IOType ioType, long perTick) {
        appendComponent(new RequirementEnergy(ioType, perTick));
    }

    private void requireFluid(IOType ioType, ILiquidStack stack, boolean isPerTick) {
        FluidStack mcFluid = CraftTweakerMC.getLiquidStack(stack);
        if (mcFluid == null) {
            CraftTweakerAPI.logError("[ModularMachinery] FluidStack not found/unknown fluid: " + stack.toString());
            return;
        }
        if (stack.getTag() != null) {
            mcFluid.tag = CraftTweakerMC.getNBTCompound(stack.getTag());
        }

        if (isPerTick) {
            appendComponent(new RequirementFluidPerTick(ioType, mcFluid));
        } else {
            appendComponent(new RequirementFluid(ioType, mcFluid));
        }
    }

    @Optional.Method(modid = "mekanism")
    private void requireGas(IOType ioType, String gasName, int amount) {
        Gas gas = GasRegistry.getGas(gasName);
        if (gas == null) {
            CraftTweakerAPI.logError("[ModularMachinery] GasStack not found/unknown gas: " + gasName);
            return;
        }
        int max = Math.max(0, amount);
        GasStack gasStack = new GasStack(gas, max);
        RequirementFluid req = RequirementFluid.createMekanismGasRequirement(RequirementTypesMM.REQUIREMENT_GAS, ioType, gasStack);
        appendComponent(req);
    }

    private void requireFuel(int requiredTotalBurnTime) {
        appendComponent(new RequirementItem(IOType.INPUT, requiredTotalBurnTime));
    }

    private void requireFuel(IOType ioType, IItemStack stack) {
        ItemStack mcStack = CraftTweakerMC.getItemStack(stack);
        if (mcStack.isEmpty()) {
            CraftTweakerAPI.logError("[ModularMachinery] ItemStack not found/unknown item: " + stack.toString());
            return;
        }
        RequirementItem ri = new RequirementItem(ioType, mcStack);
        if (stack.getTag().length() > 0) {
            ri.tag = CraftTweakerMC.getNBTCompound(stack.getTag());
            ri.previewDisplayTag = CraftTweakerMC.getNBTCompound(stack.getTag());
        }
        appendComponent(ri);
    }

    private void requireFuel(IOType ioType, String oreDictName, int amount) {
        appendComponent(new RequirementItem(ioType, oreDictName, amount));
    }

    private void requireCatalyst(String oreDictName, int amount, String[] tooltips, RecipeModifier[] modifiers) {
        RequirementCatalyst catalyst = new RequirementCatalyst(oreDictName, amount);
        for (String tooltip : tooltips) {
            catalyst.addTooltip(tooltip);
        }
        for (RecipeModifier modifier : modifiers) {
            if (modifier != null) {
                catalyst.addModifier(modifier);
            }
        }
        appendComponent(catalyst);
    }

    private void requireCatalyst(IItemStack stack, String[] tooltips, RecipeModifier[] modifiers) {
        ItemStack mcStack = CraftTweakerMC.getItemStack(stack);
        if (mcStack.isEmpty()) {
            CraftTweakerAPI.logError("[ModularMachinery] ItemStack not found/unknown item: " + stack.toString());
            return;
        }
        RequirementCatalyst catalyst = new RequirementCatalyst(mcStack);
        if (stack.getTag().length() > 0) {
            catalyst.tag = CraftTweakerMC.getNBTCompound(stack.getTag());
            catalyst.previewDisplayTag = CraftTweakerMC.getNBTCompound(stack.getTag());
        }
        for (String tooltip : tooltips) {
            catalyst.addTooltip(tooltip);
        }
        for (RecipeModifier modifier : modifiers) {
            if (modifier != null) {
                catalyst.addModifier(modifier);
            }
        }
        appendComponent(catalyst);
    }

    public void appendComponent(ComponentRequirement component) {
        this.components.add(component);
        this.lastComponent = component;
    }

    //----------------------------------------------------------------------------------------------
    // build
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public void build() {
        RecipeRegistry.getRegistry().registerRecipeEarly(this);
    }

    //----------------------------------------------------------------------------------------------
    // lingering stats
    //----------------------------------------------------------------------------------------------

    @Override
    public String getFilePath() {
        return "";
    }

    @Override
    public ResourceLocation getRecipeRegistryName() {
        return name;
    }

    @Override
    public ResourceLocation getAssociatedMachineName() {
        return machineName;
    }

    @Override
    public ResourceLocation getParentMachineName() {
        return machineName;
    }

    @Override
    public int getTotalProcessingTickTime() {
        return tickTime;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean voidPerTickFailure() {
        return doesVoidPerTick;
    }

    @Override
    public List<ComponentRequirement<?, ?>> getComponents() {
        return components;
    }

    @Override
    public Map<Class<?>, List<IEventHandler<RecipeEvent>>> getRecipeEventHandlers() {
        return recipeEventHandlers;
    }

    @Override
    public List<String> getTooltipList() {
        return toolTipList;
    }

    @Override
    public boolean isParallelized() {
        return isParallelized;
    }

    @Override
    public boolean isSingleThread() {
        return singleThread;
    }

    @Override
    public String getThreadName() {
        return threadName;
    }

    @Override
    public void loadNeedAfterInitActions() {
        for (Action needAfterInitAction : needAfterInitActions) {
            needAfterInitAction.doAction();
        }
    }
}
