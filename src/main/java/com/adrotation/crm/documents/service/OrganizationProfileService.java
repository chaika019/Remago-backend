package com.adrotation.crm.documents.service;

import com.adrotation.crm.documents.dto.OrganizationProfileDtos;
import com.adrotation.crm.documents.entity.OrganizationProfile;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.documents.repository.OrganizationProfileRepository;
import com.adrotation.crm.shared.service.TextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationProfileService {

    private final OrganizationProfileRepository repository;
    private final TextSanitizer sanitizer;

    /**
     * Используется там, где реквизиты действительно обязательны,
     * например при генерации спецификации.
     */
    @Transactional(readOnly = true)
    public OrganizationProfile require() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() ->
                        new BusinessException(
                                "Не настроены реквизиты исполнителя"
                        )
                );
    }

    /**
     * Для страницы настроек.
     *
     * Если профиля ещё нет, возвращаем пустую структуру,
     * чтобы frontend мог показать форму без красной ошибки.
     */
    @Transactional(readOnly = true)
    public OrganizationProfileDtos.Response get() {
        return repository.findFirstByOrderByIdAsc()
                .map(this::dto)
                .orElseGet(this::emptyDto);
    }

    /**
     * PATCH /api/organization-profile
     *
     * Если запись ещё не существует,
     * создаём её при первом сохранении.
     */
    @Transactional
    public OrganizationProfileDtos.Response update(
            OrganizationProfileDtos.UpdateRequest request
    ) {
        OrganizationProfile profile = repository.findFirstByOrderByIdAsc()
                .orElseGet(OrganizationProfile::new);

        if (request.legalName() != null) {
            profile.setLegalName(
                    sanitize(request.legalName())
            );
        }

        if (request.unp() != null) {
            profile.setUnp(
                    sanitize(request.unp())
            );
        }

        if (request.settlementAccount() != null) {
            profile.setSettlementAccount(
                    sanitize(request.settlementAccount())
                            .replace(" ", "")
                            .toUpperCase()
            );
        }

        if (request.bankName() != null) {
            profile.setBankName(
                    sanitize(request.bankName())
            );
        }

        if (request.bankBic() != null) {
            profile.setBankBic(
                    sanitize(request.bankBic())
                            .replace(" ", "")
                            .toUpperCase()
            );
        }

        if (request.bankCity() != null) {
            profile.setBankCity(
                    sanitize(request.bankCity())
            );
        }

        if (request.legalAddress() != null) {
            profile.setLegalAddress(
                    sanitize(request.legalAddress())
            );
        }

        if (request.postalAddress() != null) {
            profile.setPostalAddress(
                    sanitize(request.postalAddress())
            );
        }

        if (request.registrationInfo() != null) {
            profile.setRegistrationInfo(
                    sanitize(request.registrationInfo())
            );
        }

        if (request.signatoryName() != null) {
            profile.setSignatoryName(
                    sanitize(request.signatoryName())
            );
        }

        validate(profile);

        OrganizationProfile saved =
                repository.save(profile);

        return dto(saved);
    }

    /**
     * Проверка перед сохранением.
     */
    private void validate(
            OrganizationProfile profile
    ) {
        if (isBlank(profile.getLegalName())) {
            throw new BusinessException(
                    "Не указано юридическое наименование"
            );
        }

        if (
                isBlank(profile.getUnp()) ||
                        !profile.getUnp().matches("\\d{9}")
        ) {
            throw new BusinessException(
                    "УНП должен содержать 9 цифр"
            );
        }

        if (
                isBlank(profile.getSettlementAccount()) ||
                        !profile.getSettlementAccount()
                                .matches("^BY[A-Z0-9]{26}$")
        ) {
            throw new BusinessException(
                    "Расчётный счёт должен быть корректным IBAN и начинаться с BY"
            );
        }

        if (isBlank(profile.getBankName())) {
            throw new BusinessException(
                    "Не указано наименование банка"
            );
        }

        if (
                isBlank(profile.getBankBic()) ||
                        !profile.getBankBic()
                                .matches("^[A-Z0-9]{8}([A-Z0-9]{3})?$")
        ) {
            throw new BusinessException(
                    "БИК должен содержать 8 или 11 символов"
            );
        }

        if (isBlank(profile.getBankCity())) {
            throw new BusinessException(
                    "Не указан город банка"
            );
        }

        if (
                isBlank(profile.getLegalAddress()) ||
                        profile.getLegalAddress().length() < 10
        ) {
            throw new BusinessException(
                    "Укажите полный юридический адрес"
            );
        }

        if (
                isBlank(profile.getPostalAddress()) ||
                        profile.getPostalAddress().length() < 10
        ) {
            throw new BusinessException(
                    "Укажите полный почтовый адрес"
            );
        }

        if (isBlank(profile.getSignatoryName())) {
            throw new BusinessException(
                    "Не указан подписант"
            );
        }
    }

    /**
     * Дополнительная защита от null и пробелов.
     */
    private String sanitize(
            String value
    ) {
        String sanitized =
                sanitizer.plain(value);

        return sanitized == null
                ? ""
                : sanitized.trim();
    }

    private boolean isBlank(
            String value
    ) {
        return value == null ||
                value.trim().isEmpty();
    }

    private OrganizationProfileDtos.Response dto(
            OrganizationProfile profile
    ) {
        return new OrganizationProfileDtos.Response(
                profile.getId(),
                profile.getLegalName(),
                profile.getUnp(),
                profile.getSettlementAccount(),
                profile.getBankName(),
                profile.getBankBic(),
                profile.getBankCity(),
                profile.getLegalAddress(),
                profile.getPostalAddress(),
                profile.getRegistrationInfo(),
                profile.getSignatoryName()
        );
    }

    /**
     * Пустой профиль для первого открытия страницы.
     */
    private OrganizationProfileDtos.Response emptyDto() {
        return new OrganizationProfileDtos.Response(
                0L,
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}