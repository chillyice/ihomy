package top.ihomy.app.tile;

import top.ihomy.app.R;

/** 下拉栏「今日日程」磁贴:一键进入日程页的今天 */
public class PlanTileService extends DeeplinkTileService {

    @Override
    protected String tileLabel() {
        return getString(R.string.tile_plan_label);
    }

    @Override
    protected int tileIcon() {
        return R.drawable.ic_shortcut_schedule;
    }

    @Override
    protected String deeplink() {
        return "ihomy://plan?tab=today";
    }
}
