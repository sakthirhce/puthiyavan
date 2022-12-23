package com.sakthi.trade;

import java.util.Arrays;
import java.util.List;

public class StrikeFinder {
    public static void main(String[] args) {
        // Define a list of possible strike prices
        List<Double> strikes = Arrays.asList(2.5, 9.0);

        // Set the initial value of the nearest strike to the first element in the list
        double nearestStrike = strikes.get(0);

        // Iterate over the list of strikes and find the one that is nearest to 5
        for (double strike : strikes) {
            if (Math.abs(strike - 5.0) < Math.abs(nearestStrike - 5.0)) {
                nearestStrike = strike;
            }
        }

        // Print the nearest strike
        System.out.println("The nearest strike is: " + nearestStrike);
    }
}
