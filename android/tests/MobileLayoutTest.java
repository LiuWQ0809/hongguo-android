package io.github.jackotom.hongguo.android;

public final class MobileLayoutTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void eq(int expected,int actual,String message){check(expected==actual,message+" expected="+expected+" actual="+actual);}
    private static void allActionsReachable(PlayerLayoutHarness player, String context) {
        int index=0;
        for(View raw:player.phoneActions.children){
            LinearLayout row=(LinearLayout)raw;
            check(row.children.size()>=1&&row.children.size()<=5,context+" bounded nonempty action rows");
            for(View item:row.children){check(index<player.episodeActions.length&&item==player.episodeActions[index],context+" action order/identity "+index);index++;}
        }
        eq(player.episodeActions.length,index,context+" every action stays reachable exactly once");
    }
    public static void main(String[] args){
        BuildConfig.IS_TV=false;
        MainLayoutHarness main=new MainLayoutHarness();
        main.applyWebInsets(main.webContainer,12,24,18,24,320);
        eq(320,main.webContainer.bottom,"real MainActivity IME consumes max, not ignored or added");
        main.applyWebInsets(main.webContainer,12,24,18,24,0);
        eq(24,main.webContainer.bottom,"keyboard closing restores system bottom");
        main.applyWebInsets(main.webContainer,12,24,18,24,12);
        eq(24,main.webContainer.bottom,"smaller IME does not remove navigation protection");
        eq(12,main.webContainer.left,"cutout left preserved");eq(18,main.webContainer.right,"cutout right preserved");
        main.onConfigurationChanged(new Configuration(1.2f));eq(1,main.webContainer.insetRequests,"main rotation re-requests insets");eq(0,main.mediaReloads,"main rotation does not reload WebView");
        BuildConfig.IS_TV=true;main.applyWebInsets(main.webContainer,12,24,18,24,320);eq(24,main.webContainer.bottom,"TV retains old system-only inset");
        main.onConfigurationChanged(new Configuration());eq(1,main.webContainer.insetRequests,"TV does not take phone rotation path");
        BuildConfig.IS_TV=false;
        MobileLayoutPolicy.Layout portrait=MobileLayoutPolicy.phone(360,800,1);
        eq(4,portrait.columns,"portrait puts the four compact actions in one row");
        MobileLayoutPolicy.Layout landscape=MobileLayoutPolicy.phone(740,330,1);
        eq(4,landscape.columns,"short landscape keeps four actions");check(landscape.compact,"landscape compact");
        check(MobileLayoutPolicy.phone(360,640,2).columns<4,"large-font options reflow");
        for(int width:new int[]{240,280,320,360,411,480,600,740,960})for(int height:new int[]{220,280,330,420,640,800,1000})for(float scale:new float[]{.8f,1,1.3f,1.5f,2,2.5f,3}){
            MobileLayoutPolicy.Layout layout=MobileLayoutPolicy.phone(width,height,scale);
            check(layout.columns>=1&&layout.columns<=5,"bounded columns");check(layout.buttonHeight>=48,"minimum touch height");
            int control=layout.controlsHeight(height,700,80);check(control>=48,"scroll window remains tappable");
            check(control+80+48<=height||height<176,"controls leave Back bar and video strip");
        }
        PlayerLayoutHarness player=new PlayerLayoutHarness();player.applyPhoneLayout();
        eq(1,player.phoneActions.children.size(),"actual production portrait has a single row");
        eq(4,player.episodeActions.length,"production phone row has four actions");
        allActionsReachable(player,"portrait");
        for(Button b:player.episodeActions){check(b.minimumHeight>=48,"production action touch min");check(b.params.height==-2,"action grows for font rather than fixed clipping");}
        check(player.back.minimumHeight>=48&&player.back.params.width>=48,"Back has actual >=48 target");
        Object originalVideo=player.video;player.episodeActions[2].requestFocus();
        player.playerRoot.width=740;player.playerRoot.height=330;
        player.onConfigurationChanged(new Configuration());
        eq(1,player.phoneActions.children.size(),"rotation keeps compact row");
        allActionsReachable(player,"short landscape");
        check(player.playerRoot.findFocus()==player.episodeActions[2],"focused existing button preserved during reparent");
        check(player.video==originalVideo&&player.mediaReloads==0&&player.generation==9&&player.position==47000,"rotation retains video object/generation/position without reload");
        check(player.userPaused&&player.autoplay&&player.requestedSpeed==1.5f&&player.quality.equals("1080p")&&player.actualQuality.equals("720p"),"rotation retains pause, speed, queue and fallback stage");
        eq(1,player.playerRoot.insetRequests,"player rotation re-requests safe area");
        player.setPhoneSafeArea(36,0,18,24);
        eq(36,player.bottom.left,"bottom cutout left");eq(18,player.bottom.right,"bottom cutout right");eq(24,player.bottom.bottom,"bottom nav protection");eq(44,player.top.left,"top padding plus left safe area");
        player.playerRoot.width=320;player.playerRoot.height=420;player.onConfigurationChanged(new Configuration(2.5f));
        check(player.phoneActions.children.size()>1,"actual large-font actions reflow");
        check(player.phoneContent.getMeasuredHeight()>player.phoneScroller.params.height,"oversized production controls scroll instead of covering heading");
        check(player.phoneScroller.params.height+player.top.getMeasuredHeight()+24<=player.playerRoot.height,"actual bounded controls and Back remain disjoint");
        for(Button b:player.episodeActions)check(b.minimumHeight>=98&&b.maxLines==2,"large font gets growing wrapped touch controls");
        allActionsReachable(player,"short screen large font");
        PlayerLayoutHarness highDensity=new PlayerLayoutHarness();highDensity.resources.metrics.density=2.75f;
        highDensity.playerRoot.width=990;highDensity.playerRoot.height=2200;highDensity.applyPhoneLayout();
        eq(1,highDensity.phoneActions.children.size(),"fractional-density phone keeps dp columns");
        check(highDensity.back.minimumHeight>=132,"fractional-density Back target >=48dp");
        allActionsReachable(highDensity,"fractional density");
        check(highDensity.phoneScroller.params.height+highDensity.top.getMeasuredHeight()<=highDensity.playerRoot.height,"fractional-density controls fit viewport");
        int rows=player.phoneActions.children.size(),requests=player.playerRoot.insetRequests;
        BuildConfig.IS_TV=true;player.playerRoot.width=1920;player.onConfigurationChanged(new Configuration(1));
        eq(rows,player.phoneActions.children.size(),"TV never adopts phone reflow");eq(requests,player.playerRoot.insetRequests,"TV does not enter phone safe-area path");
        PlayerLayoutHarness television=new PlayerLayoutHarness();
        eq(5,television.episodeActions.length,"TV keeps exactly five original controls, no casting affordance");
        for(Button action:television.episodeActions)check(!action.text.equals("投屏"),"TV has no phone cast action");
        System.out.println("MOBILE_LAYOUT_OK assertions="+assertions+" policy=real activityMethods=real views=geometry_doubles SDK35=compiled networkRequests=0 emulator=not_used");
    }
}
