package com.test;

import com.forestfull.ConvertType;

public class Main {

    public static class DTO1{
        String string = "test";
        int iii = 0;

    }

    public static class DTO2{
        String string;
        int iii;

    }


    public static void main(String[] args) {
        DTO2 dto2 = ConvertType.from(new DTO1()).to(DTO2.class);
        System.out.println(dto2.iii + dto2.string);

        ConvertType.ConvertedMap map = ConvertType.from(new DTO1()).toMap();
        System.out.println(map.get("iii") + " " + map.get("string"));
        System.out.println(map.toJsonString());
    }
}
