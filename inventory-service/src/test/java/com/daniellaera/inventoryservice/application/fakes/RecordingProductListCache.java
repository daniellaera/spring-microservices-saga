package com.daniellaera.inventoryservice.application.fakes;

import com.daniellaera.inventoryservice.domain.port.out.ProductListCache;

public class RecordingProductListCache implements ProductListCache {

    private int invalidations;

    @Override
    public void invalidate() {
        invalidations++;
    }

    public int invalidations() {
        return invalidations;
    }
}
