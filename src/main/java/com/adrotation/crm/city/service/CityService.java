package com.adrotation.crm.city.service;

import com.adrotation.crm.city.dto.CityDtos;
import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.city.enums.RepairStatus;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.city.repository.CityRepository;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.service.TextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {
    private final CityRepository cityRepository;
    private final TextSanitizer sanitizer;
    private final DtoMapper mapper;

    public City require(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Город не найден: " + id));
    }

    @Transactional
    public City requireForUpdate(Long id) {
        return cityRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Город не найден: " + id));
    }

    public List<CityDtos.CityResponse> list(CityCategory category, Boolean needsRepair, String query) {
        String search = normalize(query);
        return cityRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .filter(city -> category == null || city.getCategory() == category)
                .filter(city -> needsRepair == null || city.isNeedsRepair() == needsRepair)
                .filter(city -> normalize(city.getName()).contains(search))
                .map(mapper::city).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
    }

    @Transactional
    public CityDtos.CityResponse create(CityDtos.CityRequest request) {
        String name = sanitizer.plain(request.name());
        cityRepository.findByNameIgnoreCase(name).ifPresent(c -> { throw new BusinessException("Город уже существует: " + c.getName()); });
        City city = new City();
        apply(city, request);
        return mapper.city(cityRepository.save(city));
    }

    @Transactional
    public CityDtos.CityResponse update(Long id, CityDtos.CityRequest request) {
        City city = require(id);
        String name = sanitizer.plain(request.name());
        cityRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(c -> { throw new BusinessException("Город уже существует: " + c.getName()); });
        apply(city, request);
        return mapper.city(cityRepository.save(city));
    }

    private void apply(City city, CityDtos.CityRequest request) {
        if (request.rotationEnabled() != null) city.setRotationEnabled(request.rotationEnabled());
        city.setName(sanitizer.plain(request.name()));
        city.setCategory(request.category());
        boolean repair = request.needsRepair() || request.category() == CityCategory.V;
        city.setNeedsRepair(repair);
        city.setStandAddress(sanitizer.plain(request.standAddress()));
        city.setStandFormat(request.standFormat());
        city.setRepairPlan(sanitizer.plain(request.repairPlan()));
        if (repair) {
            city.setRepairStatus(request.repairStatus() == null || request.repairStatus() == RepairStatus.NONE ? RepairStatus.LIST_APPROVAL : request.repairStatus());
        } else {
            city.setRepairStatus(RepairStatus.NONE);
        }
    }
}
