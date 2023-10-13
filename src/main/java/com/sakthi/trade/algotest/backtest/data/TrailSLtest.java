package com.sakthi.trade.algotest.backtest.data;

public class TrailSLtest {
    private double currentPrice;
    private double trailingStopLoss;

    private int pointMove;
    private double increaseSL;
    public TrailSLtest() {
        // Initialize with some default values
        this.currentPrice = 0.0;
        this.trailingStopLoss = 0.0;
        this.pointMove = 1;
        this.increaseSL = 4.0;
    }

    public void updatePrice(double initialSL,double entryPrice,double newPrice) {
        this.currentPrice = entryPrice;
        // Calculate the trailing stop-loss based on the price movement
        int priceMove = (int)(newPrice - entryPrice);
        if (priceMove >= this.pointMove) {
            // For every this.pointMove, increase stop-loss by this.increaseSL points
            for(int i=pointMove;i<=priceMove;i=i+this.pointMove) {
                double trailingStopLossNew = initialSL + (i * this.increaseSL);
                if (newPrice > trailingStopLossNew) {
                    trailingStopLoss = trailingStopLossNew;
                }
            }
        }
        System.out.printf("trailingStopLoss:"+trailingStopLoss);
    }

    public double getTrailingStopLoss() {
        return trailingStopLoss;
    }

    public static void main(String[] args) {

        long dd=Long.parseLong("14505730");
        System.out.println(dd);
        // Example usage
        TrailSLtest trailingStopLoss = new TrailSLtest();

        // Simulate price changes
        double initialPrice = 100.0;
        System.out.println("Initial Price: " + initialPrice);

        trailingStopLoss.updatePrice(90,initialPrice,initialPrice);

        // Price increases by 1 point
        double newPrice1 = 101.0;
        System.out.println("New Price: " + newPrice1);
        trailingStopLoss.updatePrice(90,initialPrice,newPrice1);
        System.out.println("Trailing Stop Loss: " + trailingStopLoss.getTrailingStopLoss());

        // Price increases by 3 points (total move of 4 points)
        double newPrice2 = 104.0;
        System.out.println("New Price: " + newPrice2);
        trailingStopLoss.updatePrice(90,initialPrice,newPrice2);
        System.out.println("Trailing Stop Loss: " + trailingStopLoss.getTrailingStopLoss());
    }
}
