package com.ihomy.garden.service;

import com.ihomy.garden.GardenConst;
import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.VenueDef;
import com.ihomy.garden.core.GardenGrowth;
import com.ihomy.garden.core.GardenSolarEvents;
import com.ihomy.garden.core.GardenWeatherEvents;
import com.ihomy.garden.dto.PlotVO;
import com.ihomy.garden.entity.GardenPlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 地块 → 视图装配:成长 / 阶段 / 冷却全部实时推导(不落库)。
 * mine = 可操作(共享地块对全家成员均为 true),别人的个人地块只读可见。
 */
@Service
@RequiredArgsConstructor
public class GardenPlotAssembler {

    private final GardenContent content;
    private final GardenGrowthService growth;

    public PlotVO toVO(GardenPlot p, LocalDateTime now, String myUid,
                       Map<String, String> nicknames, String season, GardenSolarEvents.Schedule events,
                       GardenWeatherEvents.Event weatherEvent) {
        PlotVO vo = new PlotVO();
        vo.setId(p.getId());
        vo.setVenue(p.getVenue());
        vo.setSlotNo(p.getSlotNo());
        vo.setOwnerUid(p.getOwnerUid());
        vo.setMine(GardenConst.mayOperate(p.getOwnerUid(), myUid));
        vo.setOwnerName(nicknames.getOrDefault(p.getOwnerUid(), null));
        vo.setSoilType(p.getSoilType());
        vo.setPrepared(p.getPrepared() != null && p.getPrepared() == 1);
        vo.setCareBoostMinutes(p.getCareBoostMinutes());
        vo.setPlantedAt(p.getPlantedAt());
        vo.setLastWateredAt(p.getLastWateredAt());
        vo.setLastSunAt(p.getLastSunAt());
        vo.setLastFertilizedAt(p.getLastFertilizedAt());

        VenueDef venue = content.venue(p.getVenue());
        boolean indoor = venue != null && venue.indoor();
        // 气温胁迫:室内与大棚免疫(与成长口径同一处判定)
        boolean sheltered = indoor || "GREENHOUSE".equals(p.getVenue());
        int tempStressBp = growth.tempStressBpOf(p.getFamilyId(), p.getCropCode(), sheltered);
        vo.setTempStress(GardenWeatherEvents.stressCode(tempStressBp));
        vo.setTempStressBp(tempStressBp == 100 ? null : tempStressBp);

        if (p.getCropCode() != null) {
            CropDef crop = content.crop(p.getCropCode());
            vo.setCropCode(p.getCropCode());
            vo.setCropName(crop == null ? p.getCropCode() : crop.name());
            vo.setForm(crop == null ? null : crop.form());
            if (crop != null) {
                GardenGrowth.GrowthView growth = GardenGrowth.evaluate(
                        crop.durationHours(), crop.soilPreference(), crop.soilCompatible(),
                        crop.hardiness(), crop.seasons(), crop.seasonMultBp(), crop.offSeasonMultBp(),
                        season, p.getVenue(), indoor, vo.isPrepared(), p.getSoilType(),
                        p.getPlantedAt(), p.getLastWateredAt(), p.getCareBoostMinutes(), now, events,
                        weatherEvent, tempStressBp);
                vo.setStage(growth.stageIndex());
                vo.setStageName(growth.stageName());
                vo.setGrowthRatioBp(growth.ratioBp());
                vo.setHarvestable(growth.harvestable());
                vo.setThirsty(growth.thirsty());
                vo.setMinutesToHarvest(growth.minutesToHarvest());
                vo.setMinutesToNextStage(growth.minutesToNextStage());
            }
        } else {
            vo.setStage(null);
            vo.setHarvestable(false);
            vo.setThirsty(false);
        }

        // 浇水冷却按家庭的试水位阶回显(12h/11h/10h),晒太阳/施肥恒 12h(详细设计 §6.4)
        vo.setWeatherEvent(weatherEvent == null ? null : weatherEvent.code());
        vo.setWaterCooldownSec(remainingSeconds(p.getLastWateredAt(), now, growth.waterCooldownMinutes(p.getFamilyId())));
        vo.setSunCooldownSec(remainingSeconds(p.getLastSunAt(), now));
        vo.setFertilizeCooldownSec(remainingSeconds(p.getLastFertilizedAt(), now));
        return vo;
    }

    /** 冷却剩余秒数(0 = 已就绪;向上取秒,FR-07 前端倒计时口径) */
    public static long remainingSeconds(LocalDateTime last, LocalDateTime now) {
        return remainingSeconds(last, now, GardenConst.CARE_COOLDOWN_MINUTES);
    }

    /** 冷却剩余秒数(可传该动作的冷却分钟数:试水改浇水冷却用) */
    public static long remainingSeconds(LocalDateTime last, LocalDateTime now, int cooldownMinutes) {
        if (last == null) {
            return 0;
        }
        LocalDateTime readyAt = last.plusMinutes(cooldownMinutes);
        if (!readyAt.isAfter(now)) {
            return 0;
        }
        return Duration.between(now, readyAt).getSeconds();
    }
}
