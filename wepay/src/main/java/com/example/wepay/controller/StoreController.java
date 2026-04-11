package com.example.wepay.controller;

import com.example.wepay.service.DailyActiveUserService;
import com.example.wepay.service.StoreLocationService;
import com.example.wepay.service.StoreRankingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRankingService storeRankingService;
    private final DailyActiveUserService dailyActiveUserService;
    private final StoreLocationService storeLocationService;

    public StoreController(StoreRankingService storeRankingService,
                           DailyActiveUserService dailyActiveUserService,
                           StoreLocationService storeLocationService) {
        this.storeRankingService = storeRankingService;
        this.dailyActiveUserService = dailyActiveUserService;
        this.storeLocationService = storeLocationService;
    }

    @PostMapping("/transactions/{storeName}")
    public void recordTransaction(@PathVariable String storeName) {
        storeRankingService.recordTransaction(storeName);
    }

    @GetMapping("/ranking")
    public List<String> getTopStores(@RequestParam(defaultValue = "10") int limit) {
        return storeRankingService.getTopStores(limit);
    }

    @PostMapping("/dau/{userId}")
    public void recordActiveUser(@PathVariable Long userId) {
        dailyActiveUserService.recordActiveUser(userId);
    }

    @GetMapping("/dau")
    public long getDailyActiveUserCount() {
        return dailyActiveUserService.getDailyActiveUserCount();
    }

    @PostMapping("/locations")
    public void addStore(@RequestParam String storeName,
                         @RequestParam double longitude,
                         @RequestParam double latitude) {
        storeLocationService.addStore(storeName, longitude, latitude);
    }

    @GetMapping("/nearby")
    public List<String> findNearbyStores(@RequestParam double lng,
                                         @RequestParam double lat,
                                         @RequestParam double radius) {
        return storeLocationService.findNearbyStores(lng, lat, radius);
    }
}
