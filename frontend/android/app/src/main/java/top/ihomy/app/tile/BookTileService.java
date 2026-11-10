package top.ihomy.app.tile;

import top.ihomy.app.R;

/** 下拉栏「记账本」磁贴:一键进入家庭记账本 */
public class BookTileService extends DeeplinkTileService {

    @Override
    protected String tileLabel() {
        return getString(R.string.tile_book_label);
    }

    @Override
    protected int tileIcon() {
        return R.drawable.ic_shortcut_book;
    }

    @Override
    protected String deeplink() {
        return "ihomy://book";
    }
}
