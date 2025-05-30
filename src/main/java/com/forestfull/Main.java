package com.forestfull;

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

    }
}
