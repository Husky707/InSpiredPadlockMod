package padlock;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.evacipated.cardcrawl.modthespire.lib.ByRef;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.actions.unique.CodexAction;
import com.megacrit.cardcrawl.actions.unique.DiscoveryAction;
import com.megacrit.cardcrawl.actions.utility.ChooseOneColorless;
import com.megacrit.cardcrawl.actions.watcher.ForeignInfluenceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.curses.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.screens.SingleCardViewPopup;
import com.megacrit.cardcrawl.screens.compendium.CardLibraryScreen;
import com.megacrit.cardcrawl.screens.mainMenu.ColorTabBar;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static padlock.PadlockMain.makeID;
import static padlock.RelicLocker.PERIOD;

public class CardLocker {

    public static Prefs saveState = SaveHelper.getPrefs("CardLocker");
    public static UIStrings uiStrings = CardCrawlGame.languagePack.getUIString(makeID("LockStrings"));
    public static String[] TEXT = uiStrings.TEXT;

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
    protected static Map<String, Boolean> LockedCards = init();

    //Public funcs/////////////////////////////////////////////////////////////////////////

    public static void lockByDefault(ArrayList<String> cardIDs){
        if(cardIDs == null)
            return;

        for(String each : cardIDs){
            if(!saveState.data.containsKey(each)){
                saveState.putBoolean(each, true);
                LockedCards.put(each, true);
            }
        }
        saveState.flush();
    }

    public static void setLockState(String cardID, boolean isLocked){
        LockedCards.put(cardID, isLocked);
        saveState.putBoolean(cardID, isLocked);
        saveState.flush();
    }

    public static void setLockState(AbstractCard c, boolean isLocked){
        setLockState(c.cardID, isLocked);
    }

    public static void setLockState(CardGroup cards, boolean lock){
        for(AbstractCard c : cards.group){
            LockedCards.put(c.cardID, lock);
            saveState.putBoolean(c.cardID, lock);
        }
        saveState.flush();
    }

    public static void setLockState(Predicate<AbstractCard> predicate, boolean lock){
        ArrayList<AbstractCard> cards = CardLibrary.getAllCards();
        for(AbstractCard c : cards){
            if(predicate.test(c)){
                LockedCards.put(c.cardID, lock);
                saveState.putBoolean(c.cardID, lock);
            }
        }
        saveState.flush();
    }

    public static void unlockAll(){
        LockedCards = new HashMap<>();
        saveState.data.clear();
        saveState.flush();
    }

    ////////////////////////////////////////////////////

    public static boolean isCardLocked(AbstractCard c){
        return LockedCards != null && LockedCards.getOrDefault(c.cardID, false);
    }
    public static boolean isCardLocked(String cardID){
        return LockedCards != null && LockedCards.getOrDefault(cardID, false);
    }


    public static boolean isCardLockable(AbstractCard c){
        if(c.type == AbstractCard.CardType.CURSE)
            return c.cardID != Necronomicurse.ID && c.cardID != AscendersBane.ID && c.cardID != Pride.ID && c.cardID != CurseOfTheBell.ID;
        return c.type != AbstractCard.CardType.STATUS && c.rarity != AbstractCard.CardRarity.BASIC && c.rarity != AbstractCard.CardRarity.SPECIAL;
    }

    protected static void removeLockedCards(ArrayList<AbstractCard> cards){
        cards.removeIf(CardLocker::isCardLocked);
    }

    protected static void removeLockedCardIDs(ArrayList<String> cards){
        cards.removeIf(CardLocker::isCardLocked);
    }

    //////////////////////////////////////////////////////////////
    //If too many cards are locked, prevent infinite loops when trying to generate unique cards //Patches at EOF

    public static ArrayList<AbstractCard> lastCreatedChoiceGroup = new ArrayList<>();
    public static void maybeForceDoup(AbstractCard card, ArrayList<AbstractCard> selection)
    {
        if(lastCreatedChoiceGroup == null)
            return;

        ArrayList<String> candidates = new ArrayList<>(selection.size() + 1);
        for(AbstractCard c : selection)
            candidates.add(c.cardID);
        candidates.add(card.cardID);
        for(AbstractCard c : lastCreatedChoiceGroup){
            if(!candidates.contains(c.cardID))
                return; //A non-doup option exists
        }

        selection.add(card.makeCopy());
    }

    ////////////////////////////////////////////////////


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

        float hbWidth =250.0F * Settings.scale;
        _hb = new Hitbox((Settings.WIDTH / 2.0F) - hbWidth/2f, Settings.HEIGHT - (120.0F * Settings.scale), hbWidth, 80.0F * Settings.scale);
        return _hb;
    };

    public static Texture lockedAttackImg, lockedAttackImg_p;
    public static Texture lockedPowerImg, lockedPowerImg_p;
    public static Texture lockedSkillImg, lockedSkillImg_p;

    public static Texture getLockImg(AbstractCard.CardType t){return(getLockImg(t, false));}
    public static Texture getLockImg(AbstractCard.CardType t, boolean portait){
        if (portait){
            switch (t) {
                case ATTACK:
                    if(lockedAttackImg_p == null)
                        lockedAttackImg_p = loadImage("attack", true);
                    return lockedAttackImg_p;
                case POWER:
                    if(lockedPowerImg_p == null)
                        lockedPowerImg_p = loadImage("power", true);
                    return lockedPowerImg_p;
                default:
                    if(lockedSkillImg_p == null)
                        lockedSkillImg_p = loadImage("skill", true);
                    return lockedSkillImg_p;
            }
        }else{
            switch (t) {
                case ATTACK:
                    if(lockedAttackImg == null)
                        lockedAttackImg = loadImage("attack");
                    return lockedAttackImg;
                case POWER:
                    if(lockedPowerImg == null)
                        lockedPowerImg = loadImage("power");
                    return lockedPowerImg;
                default:
                    if(lockedSkillImg == null)
                        lockedSkillImg = loadImage("skill");
                    return lockedSkillImg;
            }
        }
    }

    private static Texture loadImage(String type){return loadImage(type, false);}
    private static Texture loadImage(String type, boolean portrait){
        String imgUrl = "padlock/images/Locked"+type;
        if(portrait) imgUrl += "_p";
        imgUrl += ".png";

        Texture retVal = new Texture(Gdx.files.internal(imgUrl));
        retVal.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return retVal;
    }

    public static AbstractCard sourceCard;
    public static AbstractCard cardUpgClone;

    ////Patches////////////////////////////////////////////////
    @SpirePatch2(
            clz = SingleCardViewPopup.class,
            method = "render"
    )
    public static class Patch_SingleCardViewPopup_render{

        @SpireInsertPatch(rloc = 35, localvars = {"copy"})
        public static void patch(SingleCardViewPopup __instance, SpriteBatch sb, AbstractCard ___card, AbstractCard copy){
            if(!isCardLockable(___card) || CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.CARD_LIBRARY)
                return;

            boolean isLocked = isCardLocked(___card);
            cardUpgClone = copy;
            Hitbox hb = getHB();
            sb.setColor(hb.hovered ? (isLocked ? Color.RED.cpy() : Color.BLUE.cpy()) : Color.BLACK.cpy());
            sb.draw(ImageMaster.RELIC_LOCK_OUTLINE, hb.cX - 80.0F * Settings.scale - 64.0F, hb.cY - 58.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.RELIC_LOCK, hb.cX - 80.0F * Settings.scale - 64.0F, hb.cY - 58.0F, 64.0F, 64.0F, 128.0F, 128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);
            String txt = TEXT[isLocked ? 1 : 0] +" "+ uiStrings.TEXT_DICT.get("CARD") + PERIOD;
            if (hb.hovered) {
                FontHelper.renderFont(sb, FontHelper.cardTitleFont, txt, hb.cX - 45.0F * Settings.scale, hb.cY + 10.0F * Settings.scale, isLocked ? Settings.RED_TEXT_COLOR : Settings.BLUE_TEXT_COLOR);
            } else {
                FontHelper.renderFont(sb, FontHelper.cardTitleFont, txt, hb.cX - 45.0F * Settings.scale, hb.cY + 10.0F * Settings.scale, Settings.GOLD_COLOR);
            }
            hb.render(sb);
        }
    }

    @SpirePatch2(
            clz = SingleCardViewPopup.class,
            method = "close"
    )
    public static class Patch_SingleCardViewPopup_close{

        @SpireInsertPatch(rloc = 1)
        public static void patch(){
            _hb = null;
            cardUpgClone = null;
            sourceCard = null;
        }
    }

    @SpirePatch2(
            clz = SingleCardViewPopup.class,
            method = "open",
            paramtypez = {AbstractCard.class}
    )
    public static class Patch_SingleCardViewPopup_open{

        @SpireInsertPatch(rloc = 1)
        public static void patch(AbstractCard card){
            if(!isCardLockable(card) || CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.CARD_LIBRARY)
                return;

            getHB();
            sourceCard = card;
        }
    }

    @SpirePatch2(
            clz = SingleCardViewPopup.class,
            method = "open",
            paramtypez = {AbstractCard.class, CardGroup.class}
    )
    public static class Patch_SingleCardViewPopup_openGroup{

        @SpireInsertPatch(rloc = 1)
        public static void patch(AbstractCard card){
            if(!isCardLockable(card) || CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.CARD_LIBRARY)
                return;

            getHB();
            sourceCard = card;
        }
    }

    @SpirePatch2(
            clz = SingleCardViewPopup.class,
            method = "update"
    )
    public static class Patch_SingleCardViewPopup_update{

        @SpireInsertPatch(rloc = 0)
        public static void patch(AbstractCard ___card){
            if(!isCardLockable(___card) || CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.CARD_LIBRARY)
                return;

            Hitbox hb = getHB();
            hb.update();
            if (hb.hovered && InputHelper.justClickedLeft){
                hb.clickStarted = true;
                InputHelper.justClickedLeft = false; //kill the click start here to prevent card screen from closing
            }
            if (hb.clicked || CInputActionSet.map.isJustPressed()) {
                CInputActionSet.map.unpress();
                hb.clicked = false;
                //CardCrawlGame.sound.play("UI_CLICK_1");
                InputHelper.justClickedLeft = false;

                boolean locked = isCardLocked(___card);
                setLockState(___card, !locked);
                if(locked)
                    CardCrawlGame.sound.playAV("CARD_DRAW_8", -0.12F, 0.25F);
                else
                    CardCrawlGame.sound.play("POWER_SHACKLE", 0.05F);
            }
        }
    }

    @SpirePatch2(
            clz = SingleCardViewPopup.class,
            method = "renderPortrait"
    )
    public static class Patch_SingleCardViewPopup_renderPortrait {

        @SpirePostfixPatch
        public static void patch(SingleCardViewPopup __instance, SpriteBatch sb){
            if(CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.CARD_LIBRARY || sourceCard == null || !isCardLocked(sourceCard))
                return;

            Texture portraitImg = getLockImg(sourceCard.type, true);
            sb.draw(portraitImg, Settings.WIDTH / 2.0F - 250.0F, Settings.HEIGHT / 2.0F - 190.0F + 136.0F * Settings.scale, 250.0F, 190.0F, 500.0F, 380.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 500, 380, false, false);
        }
    }

    public static void renderLockPortrait(SpriteBatch sb, AbstractCard c){
        Texture portraitImg = getLockImg(c.type);
        sb.draw(portraitImg, c.current_x - 125.0F, c.current_y - 95.0F + 72.0F, 125.0F, 23.0F, 250.0F, 190.0F, c.drawScale * Settings.scale, c.drawScale * Settings.scale, c.angle, 0, 0, 250, 190, false, false);
    }

    /////////////////////////////////////////
    @SpirePatch2(
            clz = AbstractCard.class,
            method = "renderImage"
    )
    public static class Patch_AbstractCard_renderImage{

        //Renders the lock image above this card's portrait img
        @SpireInsertPatch(rloc = 30)
        public static void patch(AbstractCard __instance, SpriteBatch sb) {
            if (CardCrawlGame.mainMenuScreen.screen != MainMenuScreen.CurScreen.CARD_LIBRARY || !isCardLocked(__instance))
                return;

            renderLockPortrait(sb, __instance);
        }
    }

    ////////////////Compendium Screen: Show tip and right-click to toggle//////////////////
    @SpirePatch2(
            clz = CardLibraryScreen.class,
            method = "render"
    )
    public static class Patch_CardLibraryScreen_render{
        public static float alphaLerp = 0f;
        public static float lerpDir = 1;
        @SpirePostfixPatch()
        public static void patch(SpriteBatch sb){
            final ColorTabBar colorBar = ReflectionHacks.getPrivate(CardCrawlGame.mainMenuScreen.cardLibraryScreen, CardLibraryScreen.class, "colorBar");
            if(colorBar == null || colorBar.curTab == ColorTabBar.CurrentTab.CURSE){
                alphaLerp += 0.012f*lerpDir;
                if(alphaLerp >= 0.8f){
                    lerpDir = -1f;
                    alphaLerp = 0.8f;
                }else if (alphaLerp <= 0f){
                    lerpDir = 1f;
                    alphaLerp = 0f;
                }
                FontHelper.renderWrappedText(sb, FontHelper.tipHeaderFont, uiStrings.EXTRA_TEXT[0], 160.0F * Settings.scale, 480.0F * Settings.scale, 300f*Settings.scale, Color.WHITE.cpy(), 0.8f);
                FontHelper.renderWrappedText(sb, FontHelper.tipHeaderFont, uiStrings.EXTRA_TEXT[0], 160.0F * Settings.scale, 480.0F * Settings.scale, 300f*Settings.scale, new Color(1f, 0.01f, 0.01f, alphaLerp), 0.8f);
            }
            FontHelper.renderFont(sb, FontHelper.tipHeaderFont, TEXT[2] + uiStrings.TEXT_DICT.get("CARD"), 20.0F * Settings.scale, 310.0F * Settings.scale, Color.WHITE.cpy());
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
            clz = CardLibraryScreen.class,
            method = "update"
    )
    public static class Patch_CardLibraryScreen_update{
        public static AbstractCard rclickStartCard = null;
        @SpireInsertPatch(rloc = 13)
        public static void patch(AbstractCard ___hoveredCard){
            if(InputHelper.justClickedRight)
                rclickStartCard = ___hoveredCard;
            if(InputHelper.justReleasedClickRight && rclickStartCard != null && ___hoveredCard == rclickStartCard){
                boolean locked = isCardLocked(rclickStartCard);
                if(locked){
                    CardCrawlGame.sound.playAV("CARD_DRAW_8", -0.12F, 0.25F);
                    setLockState(rclickStartCard, false);
                }else{
                    if(isCardLockable(rclickStartCard)){
                        setLockState(rclickStartCard, true);
                        CardCrawlGame.sound.play("POWER_SHACKLE", 0.05F);
                    }else
                        playCantBuySfx();
                }
                rclickStartCard = null;
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Patches to prevent locked cards from spawning

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "addCardsIntoPool"
    )
    public static class Patch_CardLibrary_addCardsIntoPool{

        @SpirePostfixPatch
        public static void patch(ArrayList<AbstractCard> tmpPool){
            removeLockedCards(tmpPool);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "getEachRare"
    )
    public static class Patch_CardLibrary_getEachRare{

        @SpirePostfixPatch
        public static CardGroup patch(CardGroup __result){
            AbstractCard _default = __result.getTopCard();
            removeLockedCards(__result.group);
            if(__result.isEmpty())
                __result.addToTop(_default);
            lastCreatedChoiceGroup = __result.group;
            return __result;
        }
    }
    @SpirePatch2(
            clz = CardLibrary.class,
            method = "getAnyColorCard",
            paramtypez = {AbstractCard.CardRarity.class}
    )
    public static class Patch_CardLibrary_getAnyColorCard{
        @SpireInsertPatch(rloc = 10, localvars = {"anyCard"})
        public static void patch(CardGroup anyCard){
            AbstractCard _default = anyCard.getTopCard();
            removeLockedCards(anyCard.group);
            if(anyCard.isEmpty())
                anyCard.addToTop(_default);
            lastCreatedChoiceGroup = anyCard.group;
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "getAnyColorCard",
            paramtypez = {AbstractCard.CardType.class, AbstractCard.CardRarity.class}
    )
    public static class Patch_CardLibrary_getAnyColorCard_type{
        @SpireInsertPatch(rloc = 11, localvars = {"anyCard"})
        public static void patch(CardGroup anyCard){
            AbstractCard _default = anyCard.getTopCard();
            removeLockedCards(anyCard.group);
            if(anyCard.isEmpty())
                anyCard.addToTop(_default);
            lastCreatedChoiceGroup = anyCard.group;
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "getCurse",
            paramtypez = {}
    )public static class Patch_CardLibrary_getCurse{
        @SpireInsertPatch(rloc = 8, localvars = {"tmp"})
        public static void patch(ArrayList<String> tmp){
            removeLockedCardIDs(tmp);
            if(tmp.isEmpty())
                tmp.add(Clumsy.ID);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "getCurse",
            paramtypez = {AbstractCard.class, Random.class}
    )public static class Patch_CardLibrary_getCurse_params{
        @SpireInsertPatch(rloc = 12, localvars = {"tmp"})
        public static void patch(ArrayList<String> tmp){
            removeLockedCardIDs(tmp);
            if(tmp.isEmpty())
                tmp.add(Clumsy.ID);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "addRedCards",
            paramtypez = {ArrayList.class}
    )
    public static class Patch_CardLibrary_addRedCards{

        @SpirePostfixPatch
        public static void patch(ArrayList<AbstractCard> tmpPool){
            removeLockedCards(tmpPool);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "addGreenCards",
            paramtypez = {ArrayList.class}
    )
    public static class Patch_CardLibrary_addGreenCards{

        @SpirePostfixPatch
        public static void patch(ArrayList<AbstractCard> tmpPool){
            removeLockedCards(tmpPool);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "addBlueCards",
            paramtypez = {ArrayList.class}
    )
    public static class Patch_CardLibrary_addBlueCards{

        @SpirePostfixPatch
        public static void patch(ArrayList<AbstractCard> tmpPool){
            removeLockedCards(tmpPool);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "addPurpleCards",
            paramtypez = {ArrayList.class}
    )
    public static class Patch_CardLibrary_addPurpleCards{

        @SpirePostfixPatch
        public static void patch(ArrayList<AbstractCard> tmpPool){
            removeLockedCards(tmpPool);
        }
    }

    @SpirePatch2(
            clz = CardLibrary.class,
            method = "addColorlessCards",
            paramtypez = {ArrayList.class}
    )
    public static class Patch_CardLibrary_addColorlessCards{

        @SpirePostfixPatch
        public static void patch(ArrayList<AbstractCard> tmpPool){
            removeLockedCards(tmpPool);
        }
    }

    //Dungeon Patches
    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "initializeCardPools"
    )
    public static class Patch_AbstractDungeon_initializeCardPools{
        public static ArrayList<AbstractCard> preLockPool;
        @SpireInsertPatch(rloc = 23, localvars = {"tmpPool"})
        public static void patch(ArrayList<AbstractCard> tmpPool){
            preLockPool = new ArrayList<>(tmpPool.size());
            preLockPool.addAll(tmpPool);
            removeLockedCards(tmpPool);
        }

        //Force at least one default card into each pool (in case where player has locked all cards in a given pool)
        @SpireInsertPatch(rloc = 52)
        public static void patch_post(){
            if(AbstractDungeon.commonCardPool.isEmpty()){
                for(AbstractCard c : preLockPool){
                    if(c.rarity == AbstractCard.CardRarity.COMMON){
                        AbstractDungeon.commonCardPool.addToTop(c);
                        break;
                    }
                }
            }
            if(AbstractDungeon.rareCardPool.isEmpty()){
                for(AbstractCard c : preLockPool){
                    if(c.rarity == AbstractCard.CardRarity.RARE){
                        AbstractDungeon.rareCardPool.addToTop(c);
                        break;
                    }
                }
            }
            if(AbstractDungeon.uncommonCardPool.isEmpty()){
                for(AbstractCard c : preLockPool){
                    if(c.rarity == AbstractCard.CardRarity.UNCOMMON){
                        AbstractDungeon.uncommonCardPool.addToTop(c);
                        break;
                    }
                }
            }
            preLockPool.clear();
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "addColorlessCards"
    )
    public static class Patch_AbstractDungeon_addColorlessCards{
        @SpireInsertPatch(rloc = 7)
        public static void patch(CardGroup ___colorlessCardPool){
            removeLockedCards(___colorlessCardPool.group);
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "addCurseCards"
    )
    public static class Patch_AbstractDungeon_CurseCards{
        @SpirePostfixPatch
        public static void Postfix(CardGroup ___curseCardPool){
            removeLockedCards(___curseCardPool.group);
            if(___curseCardPool.isEmpty())
                ___curseCardPool.addToTop(new Clumsy());
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "getRewardCards"
    )
    public static class Patch_AbstractDungeon_getRewardCards{
        @SpireInsertPatch(rloc = 48, localvars = {"containsDupe", "retVal", "card", "rarity"})
        public static void patch(@ByRef boolean[] containsDupe, ArrayList<AbstractCard> retVal, AbstractCard card, AbstractCard.CardRarity rarity){
            if(!containsDupe[0] || card.type == AbstractCard.CardType.CURSE)
                return;

            ArrayList<String> candidates = new ArrayList<>(retVal.size() + 1);
            for(AbstractCard c : retVal)
                candidates.add(c.cardID);
            candidates.add(card.cardID);
            if (AbstractDungeon.player.hasRelic("PrismaticShard")) {
                for(AbstractCard c : lastCreatedChoiceGroup){
                    if(!candidates.contains(c.cardID))
                        return;
                }
                containsDupe[0] = false;
            } else {
                CardGroup g;
                switch (rarity){
                    case RARE: g = AbstractDungeon.rareCardPool;
                        break;
                    case UNCOMMON: g = AbstractDungeon.uncommonCardPool;
                        break;
                    case COMMON: g = AbstractDungeon.commonCardPool;
                        break;
                    default: return;
                }
                if(g.isEmpty()){
                    containsDupe[0] = false;
                    return;
                }
                for(AbstractCard c : g.group){
                    if(!candidates.contains(c.cardID))
                        return;
                }
                containsDupe[0] = false;
            }

        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "getColorlessRewardCards"
    )
    public static class Patch_AbstractDungeon_getColorlessRewardCards {
        public static ArrayList<AbstractCard> holdCards = new ArrayList<>();
        @SpireInsertPatch(rloc = 29, localvars = {"retVal", "card"})
        public static void prePatch(ArrayList<AbstractCard> retVal, AbstractCard card){
            if(!retVal.contains(card))
                return;

            ArrayList<String> candidates = new ArrayList<>(retVal.size() + 1);
            for(AbstractCard c : retVal)
                candidates.add(c.cardID);
            candidates.add(card.cardID);
            for(AbstractCard c : lastCreatedChoiceGroup){
                if(!candidates.contains(c.cardID))
                    return;
            }
            holdCards.addAll(retVal);
            retVal.clear();
        }

        @SpireInsertPatch(rloc = 41, localvars = {"retVal"})
        public static void patchPost(ArrayList<AbstractCard> retVal){
            retVal.addAll(holdCards);
            holdCards.clear();
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "getEachRare"
    )
    public static class Patch_AbstractDungeon_getEachRare {
        @SpirePostfixPatch()
        public static CardGroup patch(CardGroup __result){
            lastCreatedChoiceGroup = __result.group;
            return __result;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnRandomCard"
    )
    public static class Patch_AbstractDungeon_returnRandomCard {
        @SpireInsertPatch(rloc = 10, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomCard"
    )

    public static class Patch_AbstractDungeon_returnTrulyRandomCard {
        @SpireInsertPatch(rloc = 4, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list) {
            lastCreatedChoiceGroup = list;
        }
    }
    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomCardInCombat",
            paramtypez = {}
    )
    public static class Patch_AbstractDungeon_returnTrulyRandomCardInCombat_noParams {
        @SpireInsertPatch(rloc = 19, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomCardInCombat",
            paramtypez = {AbstractCard.CardType.class}
    )
    public static class Patch_AbstractDungeon_returnTrulyRandomCardInCombat_CardType {
        @SpireInsertPatch(rloc = 16, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomColorlessCardInCombat",
            paramtypez = {Random.class}
    )
    public static class Patch_AbstractDungeon_returnTrulyRandomColorlessCardInCombat_Random {
        @SpireInsertPatch(rloc = 6, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomColorlessCardFromAvailable",
            paramtypez = {String.class, Random.class}
    )
    public static class Patch_AbstractDungeon_returnTrulyRandomColorlessCardFromAvailable_String_Random {
        @SpireInsertPatch(rloc = 6, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomColorlessCardFromAvailable",
            paramtypez = {AbstractCard.class, Random.class}
    )
    public static class Patch_AbstractDungeon_returnTrulyRandomColorlessCardFromAvailable_AbstractCard_Random {
        @SpireInsertPatch(rloc = 6, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnTrulyRandomCardFromAvailable",
            paramtypez = {AbstractCard.class, Random.class}
    )
    public static class Patch_AbstractDungeon_returnTrulyRandomCardFromAvailable_AbstractCard_Random {
        @SpireInsertPatch(rloc = 31, localvars = {"list"})
        public static void patch(ArrayList<AbstractCard> list){
            lastCreatedChoiceGroup = list;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnColorlessCard",
            paramtypez = {AbstractCard.CardRarity.class}
    )
    public static class Patch_AbstractDungeon_returnColorlessCard_CardRarity {
        @SpireInsertPatch(rloc = 1)
        public static void patch(){
            lastCreatedChoiceGroup = AbstractDungeon.colorlessCardPool.group;
        }
    }

    @SpirePatch2(
            clz = AbstractDungeon.class,
            method = "returnColorlessCard",
            paramtypez = {}
    )
    public static class Patch_AbstractDungeon_returnColorlessCard_NoParams {
        @SpireInsertPatch(rloc = 1)
        public static void patch(){
            lastCreatedChoiceGroup = AbstractDungeon.colorlessCardPool.group;
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //Doup prevention patches
    @SpirePatch2(
            clz = DiscoveryAction.class,
            method = "generateColorlessCardChoices"
    )
    public static class Patch_DiscoveryAction_generateColorlessCardChoices {
        @SpireInsertPatch(rloc = 12, localvars = {"derp", "tmp"})
        public static void patch(ArrayList<AbstractCard> derp, AbstractCard tmp){
            boolean dupe = false;
            for(AbstractCard c : derp){
                if(c.cardID.equals(tmp.cardID)){
                    dupe = true;
                    break;
                }
            }
            if(dupe)
                maybeForceDoup(tmp, derp);
        }
    }

    @SpirePatch2(
            clz = DiscoveryAction.class,
            method = "generateCardChoices"
    )
    public static class Patch_DiscoveryAction_generateCardChoices {
        @SpireInsertPatch(rloc = 16, localvars = {"derp", "tmp"})
        public static void patch(ArrayList<AbstractCard> derp, AbstractCard tmp){
            boolean dupe = false;
            for(AbstractCard c : derp){
                if(c.cardID.equals(tmp.cardID)){
                    dupe = true;
                    break;
                }
            }
            if(dupe)
                maybeForceDoup(tmp, derp);
        }
    }

    @SpirePatch2(
            clz = CodexAction.class,
            method = "generateCardChoices"
    )
    public static class Patch_CodexAction_generateCardChoices {
        @SpireInsertPatch(rloc = 11, localvars = {"derp", "tmp"})
        public static void patch(ArrayList<AbstractCard> derp, AbstractCard tmp){
            boolean dupe = false;
            for(AbstractCard c : derp){
                if(c.cardID.equals(tmp.cardID)){
                    dupe = true;
                    break;
                }
            }
            if(dupe)
                maybeForceDoup(tmp, derp);
        }
    }

    @SpirePatch2(
            clz = ChooseOneColorless.class,
            method = "generateCardChoices"
    )
    public static class Patch_ChooseOneColorless_generateCardChoices {
        @SpireInsertPatch(rloc = 11, localvars = {"derp", "tmp"})
        public static void patch(ArrayList<AbstractCard> derp, AbstractCard tmp){
            boolean dupe = false;
            for(AbstractCard c : derp){
                if(c.cardID.equals(tmp.cardID)){
                    dupe = true;
                    break;
                }
            }
            if(dupe)
                maybeForceDoup(tmp, derp);
        }
    }

    @SpirePatch2(
            clz = ForeignInfluenceAction.class,
            method = "generateCardChoices"
    )
    public static class Patch_ForeignInfluenceAction_generateCardChoices {
        @SpireInsertPatch(rloc = 25, localvars = {"derp", "tmp"})
        public static void patch(ArrayList<AbstractCard> derp, AbstractCard tmp){
            boolean dupe = false;
            for(AbstractCard c : derp){
                if(c.cardID.equals(tmp.cardID)){
                    dupe = true;
                    break;
                }
            }
            if(dupe)
                maybeForceDoup(tmp, derp);
        }
    }

    @SpirePatch2(
            clz = NeowReward.class,
            method = "getRewardCards"
    )
    public static class Patch_NeowReward_getRewardCards{
        public static ArrayList<AbstractCard> holdRetVals = new ArrayList<>();
        @SpireInsertPatch(rloc = 22, localvars = {"retVal", "card"})
        public static void patch(ArrayList<AbstractCard> retVal, AbstractCard card){
            if(!retVal.contains(card))
                return;

            ArrayList<String> candidates = new ArrayList<>(retVal.size() + 1);
            for(AbstractCard c : retVal)
                candidates.add(c.cardID);
            candidates.add(card.cardID);
            for(AbstractCard c : lastCreatedChoiceGroup){
                if(!candidates.contains(c.cardID))
                    return;
            }
            holdRetVals.addAll(retVal);
            retVal.clear();
        }

        @SpireInsertPatch(rloc = 27, localvars = "retVal")
        public static void patch_post(ArrayList<AbstractCard> retVal){
            retVal.addAll(holdRetVals);
            holdRetVals.clear();
        }
    }

    @SpirePatch2(
            clz = NeowReward.class,
            method = "getColorlessRewardCards"
    )
    public static class Patch_NeowReward_getColorlessRewardCards{
        public static ArrayList<AbstractCard> holdRetVals = new ArrayList<>();
        @SpireInsertPatch(rloc = 10, localvars = {"retVal", "card"})
        public static void patch(ArrayList<AbstractCard> retVal, AbstractCard card){
            if(!retVal.contains(card))
                return;

            ArrayList<String> candidates = new ArrayList<>(retVal.size() + 1);
            for(AbstractCard c : retVal)
                candidates.add(c.cardID);
            candidates.add(card.cardID);
            for(AbstractCard c : lastCreatedChoiceGroup){
                if(!candidates.contains(c.cardID))
                    return;
            }
            holdRetVals.addAll(retVal);
            retVal.clear();
        }

        @SpireInsertPatch(rloc = 15, localvars = "retVal")
        public static void patch_post(ArrayList<AbstractCard> retVal){
            retVal.addAll(holdRetVals);
            holdRetVals.clear();
        }
    }

    @SpirePatch2(
            clz = CardRewardScreen.class,
            method = "draftOpen"
    )
    public static class Patch_CardRewardScreen_draftOpen{
        @SpireInsertPatch(rloc = 30, localvars = {"derp", "tmp"})
        public static void patch(ArrayList<AbstractCard> derp, AbstractCard tmp){
            for (AbstractCard c : derp) {
                if (c.cardID.equals(tmp.cardID)) {
                    maybeForceDoup(tmp, derp);
                    return;
                }
            }
        }
    }

}
