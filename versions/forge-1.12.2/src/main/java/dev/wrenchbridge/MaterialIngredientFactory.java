package dev.wrenchbridge;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.*;
import net.minecraftforge.oredict.OreDictionary;

public final class MaterialIngredientFactory implements IIngredientFactory {
  public Ingredient parse(JsonContext context, JsonObject json) {
    String prefix = json.get("prefix").getAsString();
    List<Ingredient> all = new ArrayList<>();
    for (String name : OreDictionary.getOreNames())
      if (name.startsWith(prefix) || (prefix.equals("stick") && name.startsWith("rod")))
        all.add(new net.minecraftforge.oredict.OreIngredient(name));
    return new net.minecraftforge.common.crafting.CompoundIngredient(all) {};
  }
}
