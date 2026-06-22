package padlock;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.screens.compendium.PotionViewScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static padlock.PadlockMain.makeID;

public class PotionLocker {

    public static Prefs saveState = SaveHelper.getPrefs("PotionLocker");
    public static UIStrings uiStrings = CardCrawlGame.languagePack.getUIString(makeID("LockStrings"));
    public static String[] TEXT = uiStrings.TEXT;

    private static Map<String, Boolean> init(){
        Map<String, Boolean> m = new HashMap<>();
        for(String k : saveState.get().keySet()){
            boolean isLocked = saveState.getBoolean(k);
            if(isLocked)
                m.put(k, isLocked);
        }
        return m;
    }
    public static Map<String, Boolean> LockedPotions = init();


    public static boolean isPotionLocked(String potionID){
        return LockedPotions != null && LockedPotions.getOrDefault(potionID, false);
    }

    public static void lockByDefault(ArrayList<String> potionIDs){
        if(potionIDs == null)
            return;

        for(String each : potionIDs){
            if(!saveState.data.containsKey(each)){
                saveState.putBoolean(each, true);
                LockedPotions.put(each, true);
            }
        }
        saveState.flush();
    }

    public static void setLockState(String potionID, boolean isLocked){
        if(LockedPotions == null)
            return;

        LockedPotions.put(potionID, isLocked);
        saveState.putBoolean(potionID, isLocked);
        saveState.flush();
    }

    public static String getRandomPopSFX(){
        int rand = MathUtils.random(3);
        switch (rand){
            case 1:
                return "Shatter_1";
            case 2:
                return "Shatter_2";
            default:
                return "Shatter_3";
        }
    }

    public static boolean areAllPotionsLocked(){
        if(LockedPotions.size() < PotionHelper.potions.size())
            return false;

        for(String k : LockedPotions.keySet()){
            if(!LockedPotions.get(k))
                return false;
        }
        return true;
    }


    ///Core Patch////////////////////////////////////////////////////////
    @SpirePatch2(
            clz = PotionHelper.class,
            method = "getRandomPotion",
            paramtypez = {Random.class}
    )
    public static class Patch_PotionHelper_getRandomPotion_rnd{
        @SpirePostfixPatch()
        public static AbstractPotion patch(AbstractPotion __result, Random rng){
            if(!isPotionLocked(__result.ID) || areAllPotionsLocked())
                return __result;

            String randomKey;
            do{
                randomKey = PotionHelper.potions.get(rng.random(PotionHelper.potions.size() - 1));
            }while(isPotionLocked(randomKey));
            return PotionHelper.getPotion(randomKey);
        }
    }

    @SpirePatch2(
            clz = PotionHelper.class,
            method = "getRandomPotion",
            paramtypez = {}
    )
    public static class Patch_PotionHelper_getRandomPotion{
        @SpirePostfixPatch()
        public static AbstractPotion patch(AbstractPotion __result){
            if(!isPotionLocked(__result.ID) || areAllPotionsLocked())
                return __result;

            String randomKey;
            do{
                randomKey = PotionHelper.potions.get(AbstractDungeon.potionRng.random(PotionHelper.potions.size() - 1));
            }while(isPotionLocked(randomKey));
            return PotionHelper.getPotion(randomKey);
        }
    }

    ///UI and UI Patches/////////////////////////////////////////////////////////////////

    @SpirePatch2(
            clz = AbstractPotion.class,
            method = "labRender"
    )
    public static class Patch_AbstractPotion_labRender{
        @SpireInsertPatch(rloc = 12)
        public static void patch(AbstractPotion __instance, SpriteBatch sb){
            if(!isPotionLocked(__instance.ID))
                return;

            ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE);
        }

        @SpireInsertPatch(rloc = 96)
        public static void postPatch(AbstractPotion __instance, SpriteBatch sb){
            if(!isPotionLocked(__instance.ID))
                return;

            ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT);
            //Draw mini-lock,
            float pix = 64f;
            float xPos = __instance.hb.cX + (((__instance.hb.width/2f) - (pix/4f) - 24f) * Settings.scale);
            float yPos = __instance.hb.cY - (((__instance.hb.height/2f) - (pix/4f)) * Settings.scale);
            sb.setColor(Settings.HALF_TRANSPARENT_BLACK_COLOR.cpy());
            sb.draw(ImageMaster.RELIC_LOCK_OUTLINE, xPos , yPos, pix/2f, pix/2f, pix, pix, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.RELIC_LOCK, xPos, yPos, pix/2f, pix/2f, pix, pix, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
        }
    }

    @SpirePatch2(
            clz = PotionViewScreen.class,
            method = "render"
    )
    public static class Patch_PotionViewScreen_render{
        @SpirePostfixPatch
        public static void patch(SpriteBatch sb){
            FontHelper.renderFont(sb, FontHelper.tipHeaderFont, TEXT[2] + uiStrings.TEXT_DICT.get("POTION"), 20.0F * Settings.scale, 310.0F * Settings.scale, Color.WHITE.cpy());
            FontHelper.renderFont(sb, FontHelper.tipHeaderFont, TEXT[3], 50.0F * Settings.scale, 280.0F * Settings.scale, Color.WHITE.cpy());
            float lockX = 5f;
            float lockY = 295.0f;
            sb.setColor(Color.BLACK);
            sb.draw(ImageMaster.RELIC_LOCK_OUTLINE, lockX, lockY, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.RELIC_LOCK, lockX, lockY, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
        }
    }

    @SpirePatch2(
            clz = PotionViewScreen.class,
            method = "update"
    )
    public static class Patch_PotionViewScreen_update{
        @SpirePostfixPatch()
        public static void patch(ArrayList<AbstractPotion> ___commonPotions, ArrayList<AbstractPotion> ___uncommonPotions, ArrayList<AbstractPotion> ___rarePotions ){
            if(InputHelper.justReleasedClickRight){
                AbstractPotion hoverdP = null;
                for(AbstractPotion p : ___commonPotions){
                    if(p.hb.hovered){
                        hoverdP = p;
                        break;
                    }
                }
                if(hoverdP == null){
                    for(AbstractPotion p : ___uncommonPotions) {
                        if(p.hb.hovered){
                            hoverdP = p;
                            break;
                        }
                    }
                }
                if(hoverdP == null){
                    for(AbstractPotion p : ___rarePotions) {
                        if(p.hb.hovered){
                            hoverdP = p;
                            break;
                        }
                    }
                }

                if(hoverdP == null)
                    return;

                boolean locked = isPotionLocked(hoverdP.ID);
                setLockState(hoverdP.ID, !locked);
                if(locked){
                    AbstractPotion.playPotionSound();
                }else{
                    CardCrawlGame.sound.play(getRandomPopSFX());
                }
            }
        }
    }
}
