package com.detector.esp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

public class Lang {

    private static int languageMode = 0;
    private static final String PREFS = "esp_settings";

    public static void load(Context ctx) {

        String systemLang = Locale.getDefault().getLanguage();
        if (systemLang.startsWith("in")) {
            languageMode = 2;
        } else if (systemLang.startsWith("en")) {
            languageMode = 1;
        } else {

            languageMode = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt("lang_mode", 0);
        }
    }

    public static void setLanguage(Context ctx, int mode) {
        languageMode = mode;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("lang_mode", mode).apply();
    }

    public static int getLanguageMode() { return languageMode; }
    public static boolean isEnglish() { return languageMode == 1; }
    public static boolean isIndonesian() { return languageMode == 2; }

    public static void toggleLanguage(Context ctx) {
        int newMode = (languageMode + 1) % 3;
        setLanguage(ctx, newMode);
    }

    private static final String[] LABELS_ZH = {
        "人物", "自行车", "汽车", "摩托车", "飞机", "公交车", "火车", "卡车",
        "船", "红绿灯", "消防栓", "停车标志", "停车计时器", "长椅", "鸟", "猫",
        "狗", "马", "羊", "牛", "大象", "熊", "斑马", "长颈鹿",
        "背包", "雨伞", "手提包", "领带", "手提箱", "飞盘", "滑雪板", "单板",
        "球", "风筝", "棒球棒", "棒球手套", "滑板", "冲浪板", "网球拍", "瓶子",
        "酒杯", "杯子", "叉子", "刀", "勺子", "碗", "香蕉", "苹果",
        "三明治", "橙子", "西兰花", "胡萝卜", "热狗", "披萨", "甜甜圈", "蛋糕",
        "椅子", "沙发", "盆栽", "床", "餐桌", "马桶", "电视", "笔记本电脑",
        "鼠标", "遥控器", "键盘", "手机", "微波炉", "烤箱", "烤面包机", "水槽",
        "冰箱", "书", "时钟", "花瓶", "剪刀", "泰迪熊", "吹风机", "牙刷"
    };

    private static final String[] LABELS_EN = {
        "Person", "Bicycle", "Car", "Motorcycle", "Airplane", "Bus", "Train", "Truck",
        "Boat", "Traffic Light", "Fire Hydrant", "Stop Sign", "Parking Meter", "Bench", "Bird", "Cat",
        "Dog", "Horse", "Sheep", "Cow", "Elephant", "Bear", "Zebra", "Giraffe",
        "Backpack", "Umbrella", "Handbag", "Tie", "Suitcase", "Frisbee", "Skis", "Snowboard",
        "Ball", "Kite", "Baseball Bat", "Baseball Glove", "Skateboard", "Surfboard", "Tennis Racket", "Bottle",
        "Wine Glass", "Cup", "Fork", "Knife", "Spoon", "Bowl", "Banana", "Apple",
        "Sandwich", "Orange", "Broccoli", "Carrot", "Hot Dog", "Pizza", "Donut", "Cake",
        "Chair", "Couch", "Potted Plant", "Bed", "Dining Table", "Toilet", "TV", "Laptop",
        "Mouse", "Remote", "Keyboard", "Cell Phone", "Microwave", "Oven", "Toaster", "Sink",
        "Refrigerator", "Book", "Clock", "Vase", "Scissors", "Teddy Bear", "Hair Dryer", "Toothbrush"
    };

    private static final String[] LABELS_ID = {
        "Orang", "Sepeda", "Mobil", "Motor", "Pesawat", "Bus", "Kereta", "Truk",
        "Kapal", "Lampu Lalu Lintas", "Hydran Api", "Rambu Berhenti", "Meter Parkir", "Bench", "Burung", "Kucing",
        "Anjing", "Kuda", "Domba", "Sapi", "Gajah", "Beruang", "Zebra", "Jerapah",
        "Tas Punggung", "Payung", "Tas Tangan", "Dasi", "Koper", "Frisbee", "Ski", "Papan Salju",
        "Bola", "Layang-layang", "Bisbol Bat", "Sarung Tangan Bisbol", "Skateboard", "Papan Selancar", "Raket Tenis", "Botol",
        "Gelas Anggur", "Cangkir", "Garpu", "Pisau", "Sendok", "Mangkuk", "Pisang", "Apel",
        "Roti Lapis", "Jeruk", "Brokoli", "Wortel", "Sosis", "Pizza", "Donat", "Kue",
        "Kursi", "Sofa", "Tanaman Pot", "Tempat Tidur", "Meja Makan", "Toilet", "Televisi", "Laptop",
        "Mouse", "Remote", "Keyboard", "Ponsel", "Microwave", "Oven", "Toaster", "Wastafel",
        "Kulkas", "Buku", "Jam", "Vas", "Gunting", "Beruang Teddy", "Pengering Rambut", "Sikat Gigi"
    };

    public static String[] getLabels() {
        switch (languageMode) {
            case 1: return LABELS_EN;
            case 2: return LABELS_ID;
            default: return LABELS_ZH;
        }
    }

    public static String settings() {
        switch (languageMode) {
            case 1: return "ESP Detection Settings";
            case 2: return "Pengaturan Deteksi ESP";
            default: return "ESP 检测设置";
        }
    }
    public static String person() {
        switch (languageMode) {
            case 1: return "Person";
            case 2: return "Orang";
            default: return "人物";
        }
    }
    public static String vehicle() {
        switch (languageMode) {
            case 1: return "Vehicles (Car/Motorcycle/Bus/Truck/Bicycle/Boat)";
            case 2: return "Kendaraan (Mobil/Motor/Bus/Truk/Sepeda/Kapal)";
            default: return "车辆 (汽车/摩托/公交/卡车/自行车/船)";
        }
    }
    public static String animal() {
        switch (languageMode) {
            case 1: return "Animals (Cat/Dog/Bird/Horse/Cow...)";
            case 2: return "Hewan (Kucing/Anjing/Burung/Kuda/Sapi...)";
            default: return "动物 (猫/狗/鸟/马/牛...)";
        }
    }
    public static String objects() {
        switch (languageMode) {
            case 1: return "Objects (Phone/Backpack/Bottle/Chair...)";
            case 2: return "Objek (HP/Tas Punggung/Botol/Kursi...)";
            default: return "物品 (手机/背包/瓶子/椅子...)";
        }
    }
    public static String enableAll() {
        switch (languageMode) {
            case 1: return "Enable All";
            case 2: return "Aktifkan Semua";
            default: return "全部开启";
        }
    }
    public static String disableAll() {
        switch (languageMode) {
            case 1: return "Disable All";
            case 2: return "Nonaktifkan Semua";
            default: return "全部关闭";
        }
    }
    public static String satellite() {
        switch (languageMode) {
            case 1: return "Satellite Monitor";
            case 2: return "Monitor Satelit";
            default: return "卫星实时监控";
        }
    }
    public static String language() {
        switch (languageMode) {
            case 1: return "Language: English → Ganti Bahasa Indonesia";
            case 2: return "Bahasa: Indonesia → 切换中文";
            default: return "语言: 中文 → Switch to English";
        }
    }
    public static String ok() {
        switch (languageMode) {
            case 1: return "OK";
            case 2: return "OK";
            default: return "确定";
        }
    }
    public static String cancel() {
        switch (languageMode) {
            case 1: return "Cancel";
            case 2: return "Batal";
            default: return "取消";
        }
    }
    public static String locked() {
        switch (languageMode) {
            case 1: return "LOCKED";
            case 2: return "TERKUNCI";
            default: return "锁定";
        }
    }
    public static String targets() {
        switch (languageMode) {
            case 1: return "Targets";
            case 2: return "Target";
            default: return "目标";
        }
    }
}
