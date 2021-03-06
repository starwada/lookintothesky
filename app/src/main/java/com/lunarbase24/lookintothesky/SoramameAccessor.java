package com.lunarbase24.lookintothesky;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.support.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Locale;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by Wada on 2016/07/29.
 * そらまめデータアクセッサー
 * このクラスでしてもらいたいこと
 * １：指定都道府県の測定局データを取得
 * ２：測定局の選択状態管理（設定、解除）
 * ３：指定測定局の計測データ取得
 * DBおよびWebへの問い合わせ等を行う
 * DB：測定局管理テーブル、測定データ管理テーブル
 */
public class SoramameAccessor {
    private static final String SORAPREFFILE = "SoraPrefFile";

    private static  final  String SORABASEURL="http://soramame.taiki.go.jp/";
    private static final String SORASUBURL ="MstItiran.php";
    private static final String SORADATAURL = "DataList.php?MstCode=";
    // 指定都道府県の測定局一覧取得
    private static final String SORAPREFURL ="MstItiranFrame.php?Pref=";

    // DBアクセスとWebアクセスが混同している。<-よくない。
    // Webアクセスだけはスレッド分けたい。DBアクセスのみの関数もあるが。

    // ウィジット関係
    // ウィジットIDとデータ種別登録
    // int nMstCode 測定局コード
    // int nWidgetID    ウィジットID
    // int nType    データ種別
    public static int setWidgetID(Context context, int nMstCode,  int nWidgetID, int nType){
        int rc = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getWritableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }

            String[] selectionArgs = {String.valueOf(nWidgetID)};
            Cursor c = Db.query(SoramameContract.FeedEntry.WIDGET_TABLE, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_WIDGETID + " = ?", selectionArgs, null, null, null);
            if (c.getCount() == 0) {
                ContentValues values = new ContentValues();
                values.put(SoramameContract.FeedEntry.COLUMN_NAME_CODE, nMstCode);
                values.put(SoramameContract.FeedEntry.COLUMN_NAME_WIDGETID, nWidgetID);
                values.put(SoramameContract.FeedEntry.COLUMN_NAME_DATATYPE, nType);
                // 重複は追加しない
                long newRowId = Db.insertWithOnConflict(SoramameContract.FeedEntry.WIDGET_TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            c.close();
            Db.close();
        }
        catch(SQLiteException e){
            e.printStackTrace();
        }

        return rc;
    }

    // ウィジットIDとデータ種別問い合わせ
    public static int getWidgetID(Context context, int nWidgetID){
        int type = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getWritableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }

            String[] selectionArgs = {String.valueOf(nWidgetID)};
            Cursor c = Db.query(SoramameContract.FeedEntry.WIDGET_TABLE, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_WIDGETID + " = ?", selectionArgs, null, null, null);
            if (c.getCount() > 0) {
                // 登録済であれば、更新？
                if (c.moveToFirst()) {
                    type = c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_DATATYPE));
                }
            }
            c.close();
            Db.close();
        }
        catch(SQLiteException e){
            e.printStackTrace();
        }

        return type;
    }

    // ウィジットの測定局データを取得
    @Nullable
    public static int[] getWidgetMst(Context context){
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        int[] nMstCodes = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getReadableDatabase();
            if (!Db.isOpen()) {
                return null;
            }

            // 重複なしで抽出
            Cursor c = Db.query(true, SoramameContract.FeedEntry.WIDGET_TABLE, null, null, null, null, null, null, null);
            int count = c.getCount();
            if (count > 0) {
                nMstCodes = new int[count];
                // 登録済であれば、更新？
                if (c.moveToFirst()) {
                    int i=0;
                    while(true){
                        nMstCodes[i++] = c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_CODE));
                        if(!c.moveToNext()){
                            break;
                        }
                    }
                }
            }
            c.close();
            Db.close();
        }
        catch(SQLiteException e){
            e.printStackTrace();
        }

        return nMstCodes ;
    }

    // 指定測定局のウィジットIDとデータタイプのペアを返す
    @Nullable
    public static int[] getWidgetIDByMst(Context context, int nMstCode){
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        int[] nWidgetIDs = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getReadableDatabase();
            if (!Db.isOpen()) {
                return null;
            }

            String[] selectionArgs = {String.valueOf(nMstCode)};
            Cursor c = Db.query(SoramameContract.FeedEntry.WIDGET_TABLE, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_CODE + " = ?", selectionArgs, null, null, null);
            int count = c.getCount();
            if (count > 0) {
                nWidgetIDs = new int[count*2];
                // 登録済であれば、更新？
                if (c.moveToFirst()) {
                    int i=0;
                    while(true){
                        nWidgetIDs[i++] = c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WIDGETID));
                        nWidgetIDs[i++] = c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_DATATYPE));
                        if(!c.moveToNext()){
                            break;
                        }
                    }
                }
            }
            c.close();
            Db.close();
        }
        catch(SQLiteException e){
            e.printStackTrace();
        }

        return nWidgetIDs ;
    }

    // ウィジットIDとデータ種別削除
    public static int deleteWidgetID(Context context, int nWidgetID){
        int rc = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getWritableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }

            String[] selectionArgs = {String.valueOf(nWidgetID)};
            rc = Db.delete(SoramameContract.FeedEntry.WIDGET_TABLE,
                    SoramameContract.FeedEntry.COLUMN_NAME_WIDGETID + " = ?", selectionArgs);
            Db.close();
        }
        catch(SQLiteException e){
            e.printStackTrace();
        }

        return rc;
    }

    // 測定局関係
    // 指定測定局コードのデータ取得
    public static int getStation(Context context, int nCode, String station[]){
        int rc = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;

        try {
            // まず、DBをチェックする。
            Db = DbHelper.getReadableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }

            String strWhereArg[] = {String.valueOf(nCode)};
            // 日付でソート desc 降順（新しい->古い）
            Cursor c = Db.query(SoramameContract.FeedEntry.TABLE_NAME, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_CODE + " = ?", strWhereArg, null, null, null);
            rc = c.getCount();
            if (rc > 0){
                if (c.moveToFirst()) {
                    station[0] = c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_STATION));
                    station[1] = c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_ADDRESS));
                }
            }
            c.close();
            Db.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        return rc;
    }

    // 指定都道府県の測定局データを取得
    // まずDBをクエリーし、なければWebから取得しDBに登録する。
    // int nPref 都道府県コード
    // ArrayList<Soramame> 測定局データリスト
    public static ArrayList<Soramame> getPref(Context context, int nPrefCode) {
        ArrayList<Soramame> list = null;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getReadableDatabase();
            if (!Db.isOpen()) {
                return null;
            }

            String[] selectionArgs = {String.valueOf(nPrefCode)};
            Cursor c = Db.query(SoramameContract.FeedEntry.TABLE_NAME, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_PREFCODE + " = ?", selectionArgs, null, null, null);
            if (c.getCount() > 0) {
                // DBにデータがあれば、DBから取得する。
                if (c.moveToFirst()) {
                    list = new ArrayList<Soramame>();
                    while (true) {
                        Soramame mame = new Soramame(
                                c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_CODE)),
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_STATION)),
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_ADDRESS)));
                        mame.setAllow(
                                c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_OX)),
                                c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_PM25)),
                                c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WD))
                        );
                        mame.setSelected(c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_SEL)));
                        list.add(mame);

                        if (!c.moveToNext()) {
                            break;
                        }
                    }
                }
                c.close();
                Db.close();
                return list;
            }
            c.close();
            Db.close();

            // DBに無ければ、検索してDBに登録する。
            Db = DbHelper.getWritableDatabase();
            list = getPrefWeb(Db, nPrefCode, false);
        }
        catch (SQLiteException e){
            e.printStackTrace();
        }
        finally {
            Db.close();
        }

        return list;
    }

    // 測定局更新
    // 指定都道府県の測定局データをWebより再取得し、既存DB内容を更新する。
    // 測定局データも更新されることがあるので。
    public static ArrayList<Soramame> updatePref(Context context, int nPrefCode){
        int rc = 0;
        ArrayList<Soramame> list = null;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getWritableDatabase();
            if (!Db.isOpen()) {
                return null;
            }
            list = getPrefWeb(Db, nPrefCode, true);
        }
        catch (SQLiteException e){
            e.printStackTrace();
        }
        finally {
            Db.close();
        }

        return list;
    }

    // 指定都道府県の測定局データをWebにて取得
    public static ArrayList<Soramame> getPrefWeb(SQLiteDatabase Db, int nPrefCode, boolean bUpdate){
        ArrayList<Soramame> list = null;
        try{
            // DBに無ければ、検索してDBに登録する。
            String strOX;
            String strPM25;
            String strWD;
            String url = String.format(Locale.ENGLISH, "%s%s%d", SORABASEURL, SORAPREFURL, nPrefCode);
            Document doc = Jsoup.connect(url).get();
            Elements elements = doc.getElementsByAttributeValue("name", "Hyou");
            for (Element element : elements) {
                if (element.hasAttr("src")) {
                    url = element.attr("src");
                    String soraurl = SORABASEURL + url;

                    Document sora = Jsoup.connect(soraurl).get();
                    Element body = sora.body();
                    Elements tables = body.getElementsByTag("tr");
                    url = "";
                    Integer cnt = 0;
                    if (list != null) {
                        list.clear();
                    }
                    list = new ArrayList<Soramame>();

                    int rows = 0;
                    for (Element ta : tables) {
                        if (cnt++ > 0) {
                            Elements data = ta.getElementsByTag("td");
                            // 測定対象取得 OX(8)、PM2.5(13)、風向(15)
                            // 想定は○か✕
                            strOX = data.get(8).text();
                            strPM25 = data.get(13).text();
                            strWD = data.get(15).text();
                            // 最後のデータが空なので
                            if (strPM25.length() < 1) {
                                break;
                            }

                            int nCode = strPM25.codePointAt(0);
                            // PM2.5測定局のみ ○のコード(9675)
                            //if( nCode == 9675 ) {
                            Soramame ent = new Soramame(Integer.parseInt(data.get(0).text()), data.get(1).text(), data.get(2).text());
                            if (ent.setAllow(strOX, strPM25, strWD)) {
                                // ここで、登録か更新か処理を分ける。更新は今のところ、住所のみ？
                                // bUpdate
                                // 測定局DBに保存
                                ContentValues values = new ContentValues();
                                values.put(SoramameContract.FeedEntry.COLUMN_NAME_ADDRESS, data.get(2).text());
                                if(!bUpdate) {
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_IND, cnt);
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_STATION, data.get(1).text());
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_CODE, Integer.valueOf(data.get(0).text()));
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_PREFCODE, nPrefCode);
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_OX, ent.getAllow(0) ? 1 : 0);
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_PM25, ent.getAllow(1) ? 1 : 0);
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_WD, ent.getAllow(2) ? 1 : 0);
                                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_SEL, 0);
                                    // 重複は追加しない
                                    long newRowId = Db.insertWithOnConflict(SoramameContract.FeedEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                                }
                                // 更新
                                else{
                                    String strWhereCause;
                                    strWhereCause = SoramameContract.FeedEntry.COLUMN_NAME_CODE + " = ?";
                                    String strWhereArg[] = {data.get(0).text()};
                                    rows = Db.update(SoramameContract.FeedEntry.TABLE_NAME, values, strWhereCause, strWhereArg);
                                    if(rows > 0){
                                        // 選択状態を復帰させる
                                        Cursor c = Db.query(SoramameContract.FeedEntry.TABLE_NAME, null,
                                                SoramameContract.FeedEntry.COLUMN_NAME_CODE + " = ?", strWhereArg, null, null, null);
                                        if(c.getCount() > 0 && c.moveToFirst()){
                                            ent.setSelected(c.getInt(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_SEL)));
                                        }
                                        c.close();
                                    }
                                }
                                // ここで、表示用のリストに追加する
                                list.add(ent);
                            }
                            //}
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        catch (SQLiteException e){
            e.printStackTrace();
        }

        return list;
    }

    // 計測データテーブル関係
    // Context
    // ArrayList<Soramame>
    // int 更新時間（分）
    public static int getSoramameData(Context context, ArrayList<Soramame> mList, int nGap){
        int rc = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        String strMstURL = "";
        nGap += 120;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getWritableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }
            GregorianCalendar now = new GregorianCalendar(Locale.JAPAN);

            for (Soramame soramame : mList) {
                rc = 0;
                // 計測時間との差をみる、データが存在しない場合もfalseとなる。
                // 内部データ（mList）が有効な場合は不要なDBアクセスもしない。
                // 次の更新まで120分+更新時間と想定、比較先は１時間前のデータなので。
                if (soramame.isLoaded(now, nGap)) {
                    rc = 1;
                    continue;
                }
                // ここで、指定測定局のデータがDBにあるかチェックする
                // checkDB()内にて、soramame.m_aDataをクリアして、DBからのデータを保持する。
                rc = checkDB(soramame, Db);
                if (rc != 1) {
                    // DBからデータは取得したが、現在時間とのチェックを行う。
                    if (soramame.isLoaded(now, nGap)) {
                        rc = 2;
                        continue;
                    }
                }

                // 現在時間と測定最新時間を比べるともっと早くなる。
                // サイトからデータを取得する際はDBに保持する。その後、DBからmListに設定する。
                String url = String.format(Locale.ENGLISH, "%s%s%d", SORABASEURL, SORADATAURL, soramame.getMstCode());
                Document doc = Jsoup.connect(url).get();
                Elements elements = doc.getElementsByAttributeValue("name", "Hyou");
//                Integer size = elements.size();
                for (Element element : elements) {
                    if (element.hasAttr("src")) {
                        url = element.attr("src");
                        strMstURL = SORABASEURL + url;
                        // ここでは、測定局のURL解決まで、URLを次のアクティビティに渡す。
                        break;
                    }
                }
                Document sora = Jsoup.connect(strMstURL).get();
                Element body = sora.body();
                Elements tables = body.getElementsByAttributeValue("align", "right");

                // SoramameにisLoaded(西暦、月、日、時間)を実装する。
                // 内部データが無い場合はfalse。
                // 内部データの先頭要素にて判定する。入力より古いとtrue、同じか新しいとfalse。新しいは無いと思うが。
                // 新規データをテンポラリ配列に保持しておき、判定でfalseになったら、元データを取り込み、入れ替える。
                // Collections.copy()
                // 注意！そらまめのデータに、ごっそり存在しないような場合もあった。
                // ある時間のデータが無い。
                rc = 0;
                Soramame aData = new Soramame();
                int count = 0;
                for (Element ta : tables) {
                    Elements data = ta.getElementsByTag("td");
                    // 0 西暦/1 月/2 日/3 時間
                    // 4 SO2/5 NO/6 NO2/7 NOX/8 CO/9 OX/10 NMHC/11 CH4/12 THC/13 SPM/14 PM2.5/15 SP/16 WD/17 WS
                    if (soramame.isLoaded(data.get(0).text(), data.get(1).text(), data.get(2).text(), data.get(3).text(), 60)) {
                        break;
                    }
                    // このままだと、再ロードした際に、追加データ（新しい）が後に配置される。
                    aData.setData(data.get(0).text(), data.get(1).text(), data.get(2).text(), data.get(3).text(),
                            data.get(9).text(), data.get(14).text(), data.get(16).text(), data.get(17).text());

                    ContentValues values = new ContentValues();
                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_CODE, soramame.getMstCode());
                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_DATE, data.get(0).text() + " " + data.get(1).text() + " " + data.get(2).text() + " " + data.get(3).text());
                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_OX, Soramame.getValue(data.get(9).text(), -0.1f));
                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_PM25, Soramame.getValue(data.get(14).text(), -100));
                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_WD, data.get(16).text());
                    values.put(SoramameContract.FeedEntry.COLUMN_NAME_WS, Soramame.getValue(data.get(17).text(), -0.1f));
                    // 重複は追加しない
                    long newRowId = Db.insertWithOnConflict(SoramameContract.FeedEntry.DATA_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                    count++;
                }
                // countにて新しいデータが無い場合はスルー
                if (count > 0) {
                    if (soramame.getSize() > 0) {
                        aData.addAll(aData.getSize(), soramame.getData());
                        soramame.getData().clear();
                    }
                    soramame.addAll(0, aData.getData());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Db.close();
        }
        return rc;
    }

    // soramame 測定局データ
    // db DB
    // 返り値：0    正常終了/1　DBに指定測定局データが無い（サイトからデータを取得する）
    private static int checkDB(Soramame soramame, SQLiteDatabase db) {
        int rc = 0;

        try {
            if (!db.isOpen()) {
                return -1;
            }
            String strWhereArg[] = {String.valueOf(soramame.getMstCode())};
            // 日付でソート desc 降順（新しい->古い）
            Cursor c = db.query(SoramameContract.FeedEntry.DATA_TABLE_NAME, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_CODE + " = ?", strWhereArg, null, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_DATE + " desc");
//            SoramameContract.FeedEntry.COLUMN_NAME_DATE + " asc"); // <- 昇順（古い->新しい）
            if (c.getCount() > 0) {
                soramame.clearData();

                if (c.moveToFirst()) {
                    while (true) {
                        soramame.setData(
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_DATE)),
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_OX)),
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_PM25)),
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WD)),
                                c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WS))
                        );

                        if (!c.moveToNext()) {
                            break;
                        }
                    }
                }
            } else {
                // DBにデータが無い
                rc = 1;
            }
            c.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }

        return rc;
    }

	// 測定データ削除
	// 日付指定にて古いデータを削除し、データベースを圧縮する
    public static int deleteSoramameByDate(Context context, String sDate){
        int rc = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;
        try {
            // まず、DBをチェックする。
            Db = DbHelper.getWritableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }

        	// 日付指定は月単位（2017 07 *）で
            String[] selectionArgs = {sDate};
            rc = Db.delete(SoramameContract.FeedEntry.DATA_TABLE_NAME,
                    SoramameContract.FeedEntry.COLUMN_NAME_DATE + " = ?", selectionArgs);
            Db.close();
        }
        catch(SQLiteException e){
            e.printStackTrace();
        }

        return rc;
    }

	// DBデータをCSVに出力する
	// 出力先：ダウンロード\lookintothesky\lookintothesky_201707.csv
	// DIRECTORY_DOWNLOADS
	// 先頭に測定局データを出力し、その後測定データを出力する
	// 測定局データ部
	// 測定局名,測定局コード,住所,県,OX,PM2.5,風速
	// 測定データ部
	// 測定局コード,日付,OX値,PM2.5値,風向,風速
    public static int outputDBToCSV(Context context) {
        int rc = 0;
        SoramameSQLHelper DbHelper = new SoramameSQLHelper(context);
        SQLiteDatabase Db = null;

        try {
            // まず、DBをチェックする。
            Db = DbHelper.getReadableDatabase();
            if (!Db.isOpen()) {
                return -1;
            }

            // CSVファイル作成
            StringBuilder csvLine = new StringBuilder();

            // 測定局データ出力
            String strWhereArg[] = {"1"};
        	ArrayList<String> ArgList = new ArrayList<String>();
            
            // 測定局コードにてソート
        	csvLine.append("測定局名,測定局コード,住所,県コード,OX,PM2.5,風速\n");
            Cursor c = Db.query(SoramameContract.FeedEntry.TABLE_NAME, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_SEL + " = ?", strWhereArg, null, null, null);
            rc = c.getCount();
            if (rc > 0) {
                if (c.moveToFirst()) {
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_STATION)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_CODE)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_ADDRESS)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_PREFCODE)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_OX)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_PM25)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WD)) + "\n");
                	
                	ArgList.add(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_CODE)));
                }
            }
            c.close();

            // 測定データ出力
            // 測定局コードおよび日付にてソート
        	csvLine.append("測定局コード,日付,OX値,PM2.5値,風向,風速\n");
            c = Db.query(SoramameContract.FeedEntry.DATA_TABLE_NAME, null,
                    SoramameContract.FeedEntry.COLUMN_NAME_CODE + " = ?", strWhereArg, null, null, null);
            rc = c.getCount();
            if (rc > 0) {
                if (c.moveToFirst()) {
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_CODE)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_DATE)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_OX)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_PM25)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WD)) + ",");
                    csvLine.append(c.getString(c.getColumnIndexOrThrow(SoramameContract.FeedEntry.COLUMN_NAME_WS)) + "\n");
                }
            }
            c.close();

            Db.close();

            // CSV出力
            FileOutputStream out = null;
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + String.format("/sora.csv"));

            out = context.openFileOutput(file.getPath(), MODE_PRIVATE );
            out.write(csvLine.toString().getBytes());
            out.close();

        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        catch( FileNotFoundException e){
            e.printStackTrace();
        }
        catch ( IOException e){
            e.printStackTrace();
        }
        return rc;
    }

}
