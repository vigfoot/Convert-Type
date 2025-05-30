package com.test;

import com.forestfull.ConvertType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    public static class DTO1 {
        String string = "test";
        int[] iii = new int[]{1, 2};
        List<DTO3> dto3List = Collections.singletonList(new DTO3());
    }

    public static class DTO3 {
        String string = "test";
        DTO2 iiid;

    }

    public static class DTO2 {
        String string;
        int[] iii;

    }


    public static void main(String[] args) {
        DTO2 dto2 = ConvertType.from(new DTO1()).to(DTO2.class);
        System.out.println(dto2.iii[0] + " " + dto2.iii[1] + " " + dto2.string);

        ConvertType.ConvertedMap map = ConvertType.from(new DTO1()).toMap();
        System.out.println(map.get("iii") + " " + map.get("string"));
        System.out.println(map.toJsonString());
    }
}
