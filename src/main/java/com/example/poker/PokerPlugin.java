package com.example.poker;

import com.example.poker.command.PokerCommand;
import com.example.poker.listener.PokerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PokerPluginのメインクラスです。
 * PaperMCサーバー上で稼働するプラグインのエントリーポイントとなります。
 * JavaPluginを継承することで、サーバーからのライフサイクルイベント（起動・終了など）を受け取ることができます。
 */
public final class PokerPlugin extends JavaPlugin {

    private PokerManager pokerManager;

    /**
     * プラグインがサーバーによって有効化（Enable）された際に呼び出されるメソッドです。
     * コマンドの登録、イベントリスナーの登録、設定ファイルの読み込み、
     * データベースとの接続など、プラグインが動作するために必要な初期化処理はここで行います。
     */
    @Override
    public void onEnable() {
        pokerManager = new PokerManager(this);

        // コマンドとリスナーの登録
        if (getCommand("poker") != null) {
            getCommand("poker").setExecutor(new PokerCommand(pokerManager));
        }
        if (getCommand("pokeraction") != null) {
            getCommand("pokeraction").setExecutor(new com.example.poker.command.PokerActionCommand(pokerManager));
        }
        getServer().getPluginManager().registerEvents(new PokerListener(pokerManager), this);

        // サーバーのコンソールに起動完了のログメッセージを出力します。
        // getLogger().info() は通常の情報ログ（INFOレベル）として表示されます。
        getLogger().info("PokerPluginが正常に起動し、有効化されました！");
    }

    /**
     * プラグインがサーバーによって無効化（Disable）された際に呼び出されるメソッドです。
     * サーバーの停止時や、プラグインの再読み込み（リロード）時に実行されます。
     * メモリの解放、データの保存、データベース接続の切断など、
     * 安全にプラグインを終了させるためのクリーンアップ処理はここで行います。
     */
    @Override
    public void onDisable() {
        // 全てのテーブルのエンティティをクリーンアップ
        if (pokerManager != null) {
            pokerManager.getTables().forEach(table -> table.removeEntities());
        }
        // サーバーのコンソールに終了のログメッセージを出力します。
        getLogger().info("PokerPluginが無効化されました。安全に終了します。");
    }
    
    public PokerManager getPokerManager() {
        return pokerManager;
    }
}
