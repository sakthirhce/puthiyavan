package com.sakthi.trade;

import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class TokenBucket {
    private long lastRefillTime;
    private long tokens;
    private final long capacity;
    private final long refillAmount;
    private final long refillTime;
    public static final Logger LOGGER = LoggerFactory.getLogger(TokenBucket.class.getName());
    public TokenBucket(long capacity, long refillAmount, long refillTime) {
        this.capacity = capacity;
        this.refillAmount = refillAmount;
        this.refillTime = refillTime;
        this.lastRefillTime = System.currentTimeMillis();
        this.tokens = capacity;
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens > 0) {
            tokens--;
            return true;
        } else {
            return false;
        }
    }

    private void refill() {
        long now = System.currentTimeMillis();
        if (now > lastRefillTime + refillTime) {
            long elapsed = now - lastRefillTime;
            long tokensToAdd = elapsed / refillTime * refillAmount;
            tokens = Math.min(tokens + tokensToAdd, capacity);
            lastRefillTime = now;
        }
    }
    public synchronized boolean tryConsumeWithWait() throws InterruptedException {
        refill();
        while (tokens == 0) {
            LOGGER.info("rate limit exceeded, waiting");
            wait(refillTime);
            LOGGER.info("rate limit refreshed, proceeding");
            refill();
        }
        tokens--;
        return true;
    }

}
