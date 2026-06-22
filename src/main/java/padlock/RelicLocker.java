package padlock;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.screens.SingleRelicViewPopup;
import com.megacrit.cardcrawl.screens.compendium.RelicViewScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static padlock.PadlockMain.makeID;

public class RelicLocker {

    public static Prefs saveState = SaveHelper.getPrefs("RelicLocker");
    public static UIStrings uiStrings = CardCrawlGame.languagePack.getUIString(makeID("LockStrings"));
    public static String[] TEXT = uiStrings.TEXT;
    public static String PERIOD = CardCrawlGame.languagePack.getUIString("Period").TEXT[0];

    public static Map<String, Boolean> init(){
        Map<String, Boolean> m = new HashMap<>();
        if(saveState == null)
            return m;

        for(String k : saveState.get().keySet()){
            boolean isLocked = saveState.getBoolean(k);
            if(isLocked)
                m.put(k, isLocked);
        }
        return m;
    }

    protected static Map<String, Boolean> LockedRelics = init();

    //Public funcs/////////////////////////////////////////////////////////////////////////

    public static void lockByDefault(ArrayList<String> relicIDs){
        if(relicIDs == null)
            return;

        for(String each : relicIDs){
            if(!saveState.data.containsKey(each)){
                saveState.putBoolean(each, true);
                LockedRelics.put(each, true);
            }
        }
        saveState.flush();
    }

    public static void setLockState(String relicID, boolean isLocked){
        LockedRelics.put(relicID, isLocked);
        saveState.putBoolean(relicID, isLocked);
        saveState.flush();
    }

    public static void setLockState(AbstractRelic r, boolean isLocked){
        setLockState(r.relicId, isLocked);
    }

    public static void setLockState(ArrayList<AbstractRelic> relicList, boolean lock){
        for(AbstractRelic r : relicList){
            LockedRelics.put(r.relicId, lock);
            saveState.putBoolean(r.relicId, lock);
        }
        saveState.flush();
    }

    public static void unlockAll(){
        LockedRelics = new HashMap<>();
        saveState.data.clear();
        saveState.flush();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isRelicLockable(AbstractRelic r){
        return (r.tier != AbstractRelic.RelicTier.SPECIAL && r.tier != AbstractRelic.RelicTier.STARTER);
    }

    public static boolean isRelicLocked(String relicID){
        return LockedRelics != null && LockedRelics.getOrDefault(relicID, false);
    }
    public static boolean isRelicLocked(AbstractRelic r){
        return LockedRelics != null && LockedRelics.getOrDefault(r.relicId, false);
    }

    public static void playCantBuySfx() {
        int roll = MathUtils.random(2);
        if (roll == 0) {
            CardCrawlGame.sound.play("VO_MERCHANT_2A");
        } else if (roll == 1) {
            CardCrawlGame.sound.play("VO_MERCHANT_2B");
        } else {
            CardCrawlGame.sound.play("VO_MERCHANT_2C");
        }
    }

    protected static Hitbox _hb;
    public static Hitbox getHB(){
        if(_hb != null)
            return _hb;

        float hbWidth = 250.0F * Settings.scale;
        _hb = new Hitbox((Settings.WIDTH / 2.0F) - hbWidth/2f, Settings.HEIGHT - (200.0F * Settings.scale), hbWidth, 80.0F * Settings.scale);
        return _hb;
    };

    ///Core Patch////////////////////////////////////////////////////////
    @SpirePatch2(
            clz = RelicLibrary.class,
            method = "populateRelicPool"
    )
    public static class Patch_RelicLibrary_populateRelicPool{
        @SpirePostfixPatch()
        public static void patch(ArrayList<String> pool){
            pool.removeIf(RelicLocker::isRelicLocked);
        }
    }

    ///UI and UI Patches/////////////////////////////////////////////////////////////////

    @SpirePatch2(
            clz = RelicViewScreen.class,
            method = "render"
    )
    public static class Patch_RelicViewScreen_render{
        @SpirePostfixPatch()
        public static void patch(SpriteBatch sb){
            FontHelper.renderFont(sb, FontHelper.tipHeaderFont, TEXT[2] + uiStrings.TEXT_DICT.get("RELIC"), 20.0F * Settings.scale, 310.0F * Settings.scale, Color.WHITE.cpy());
            FontHelper.renderFont(sb, FontHelper.tipHeaderFont, TEXT[3], 50.0F * Settings.scale, 280.0F * Settings.scale, Color.WHITE.cpy());
            float lockX = (16f-64f)*Settings.scale;
            float lockY = 290.0f*Settings.scale;
            sb.setColor(Color.BLACK);
            sb.draw(ImageMaster.RELIC_LOCK_OUTLINE, lockX, lockY, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.RELIC_LOCK, lockX, lockY, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
        }
    }

    @SpirePatch2(
            clz = RelicViewScreen.class,
            method = "update"
    )
    public static class Patch_RelicViewScreen_update{
        public static AbstractRelic rclickStartRelic = null;
        @SpireInsertPatch(rloc = 17)
        public static void patch(AbstractRelic ___hoveredRelic){
            if(InputHelper.justClickedRight)
                rclickStartRelic = ___hoveredRelic;
            if(InputHelper.justReleasedClickRight && rclickStartRelic != null && ___hoveredRelic == rclickStartRelic){
                boolean locked = isRelicLocked(rclickStartRelic);
                if(locked){
                    rclickStartRelic.playLandingSFX();
                    setLockState(rclickStartRelic, false);
                }
                else{
                    if(isRelicLockable(rclickStartRelic)){
                        CardCrawlGame.sound.play("POWER_SHACKLE", 0.05F);
                        setLockState(rclickStartRelic, true);
                    }else
                        playCantBuySfx();
                }
                rclickStartRelic = null;
            }
        }
    }

    @SpirePatch2(
            clz = SingleRelicViewPopup.class,
            method = "renderRelicImage"
    )
    public static class Patch_SingleRelicViewPopup_renderRelicImage{
        @SpireInsertPatch(rloc = 64)
        public static void patch_pre(SpriteBatch sb, AbstractRelic ___relic, float ___RELIC_OFFSET_Y){
            if(!RelicLocker.isRelicLocked(___relic))
                return;

            ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE);
        }

        @SpirePostfixPatch()
        public static void patch_post(SpriteBatch sb){
            ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT);
        }
    }

    @SpirePatch2(
            clz = SingleRelicViewPopup.class,
            method = "render"
    )
    public static class Patch_SingleRelicViewPopup_{
        @SpirePostfixPatch()
        public static void patch(SpriteBatch sb, AbstractRelic ___relic){
            if(CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.RELIC_VIEW || !isRelicLockable(___relic))
                return;

            boolean isLocked = isRelicLocked(___relic);
            Hitbox hb = getHB();
            sb.setColor(hb.hovered ? (isLocked ? Color.RED.cpy() : Color.BLUE.cpy()) : Color.BLACK.cpy());
            sb.draw(ImageMaster.RELIC_LOCK_OUTLINE, hb.cX - 80.0F * Settings.scale - 64.0F, hb.cY - 58.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.RELIC_LOCK, hb.cX - 80.0F * Settings.scale - 64.0F, hb.cY - 58.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            String txt = TEXT[isLocked ? 1 : 0] +" "+ uiStrings.TEXT_DICT.get("RELIC") + PERIOD;
            if (hb.hovered) {
                FontHelper.renderFont(sb, FontHelper.cardTitleFont, txt, hb.cX - 45.0F * Settings.scale, hb.cY + 10.0F * Settings.scale, isLocked ? Settings.RED_TEXT_COLOR : Settings.BLUE_TEXT_COLOR);
            } else {
                FontHelper.renderFont(sb, FontHelper.cardTitleFont, txt, hb.cX - 45.0F * Settings.scale, hb.cY + 10.0F * Settings.scale, Settings.GOLD_COLOR);
            }
            hb.render(sb);
        }
    }

    @SpirePatch2(
            clz = SingleRelicViewPopup.class,
            method = "update"
    )
    public static class Patch_SingleRelicViewPopup_update{

        @SpireInsertPatch(rloc = 0)
        public static void patch(AbstractRelic ___relic){
            if(!isRelicLockable(___relic) || CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.RELIC_VIEW)
                return;

            Hitbox hb = getHB();
            hb.update();
            if (hb.hovered && InputHelper.justClickedLeft){
                hb.clickStarted = true;
                InputHelper.justClickedLeft = false; //kill the click start here to prevent card screen from closing
                InputHelper.justReleasedClickLeft = false;
            }
            if (hb.clicked || CInputActionSet.map.isJustPressed()) {
                CInputActionSet.map.unpress();
                hb.clicked = false;
                CardCrawlGame.sound.play("UI_CLICK_1");
                InputHelper.justClickedLeft = false;
                InputHelper.justReleasedClickLeft = false;

                boolean locked = isRelicLocked(___relic);
                setLockState(___relic, !locked);
                if(locked)
                    ___relic.playLandingSFX();
                else
                    CardCrawlGame.sound.play("POWER_SHACKLE", 0.05F);
            }
        }
    }

    @SpirePatch2(
            clz = AbstractRelic.class,
            method = "render",
            paramtypez = {SpriteBatch.class, boolean.class, Color.class}
    )
    public static class Patch_AbstractRelic_render{
        @SpireInsertPatch(rloc = 56)
        public static void patch_pre(AbstractRelic __instance, SpriteBatch sb){
            if(CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.RELIC_VIEW || !isRelicLocked(__instance.relicId))
                return;

            //Grayscale relic
            ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE);
        }

        @SpireInsertPatch(rloc = 73)
        public static void patch_post(AbstractRelic __instance, SpriteBatch sb){
            if(CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.RELIC_VIEW || !isRelicLocked(__instance.relicId))
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
}
