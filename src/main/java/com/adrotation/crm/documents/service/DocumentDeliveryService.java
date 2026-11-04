package com.adrotation.crm.documents.service;

import com.adrotation.crm.company.entity.Contact;
import com.adrotation.crm.company.enums.DeliveryChannel;
import com.adrotation.crm.company.repository.ContactRepository;
import com.adrotation.crm.documents.dto.SpecificationDtos;
import com.adrotation.crm.documents.entity.DocumentDelivery;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.enums.DocumentStatus;
import com.adrotation.crm.documents.repository.DocumentDeliveryRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.TextSanitizer;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DocumentDeliveryService {

    private final DocumentDeliveryRepository deliveryRepository;
    private final ContactRepository contactRepository;
    private final SpecificationService specificationService;
    private final LocalDocumentStorageService storage;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TextSanitizer sanitizer;

    @Value("${app.delivery.telegram-bot-token:}")
    private String telegramBotToken;

    @Value("${app.delivery.email-from:}")
    private String emailFrom;

    @Transactional
    public SpecificationDtos.DeliveryResponse deliver(
            Long specificationId,
            SpecificationDtos.DeliveryRequest request
    ) {
        Specification specification = specificationService.require(specificationId);
        Contact contact = resolveContact(request.contactId(), specification);

        DocumentDelivery delivery = new DocumentDelivery();
        delivery.setSpecification(specification);
        delivery.setContact(contact);
        delivery.setChannel(request.channel());
        delivery.setStatus("PENDING");
        delivery = deliveryRepository.save(delivery);

        try {
            String recipient = switch (request.channel()) {
                case EMAIL -> sendEmail(specification, contact, request.recipient());
                case TELEGRAM -> sendTelegram(specification, contact, request.recipient());
                case MANUAL -> manualRecipient(request.recipient());
                case VIBER -> throw new BusinessException(
                        "Viber Bot требует публичный URL файла и Viber user id. "
                                + "Для локальной разработки используйте ручную отправку."
                );
            };

            delivery.setRecipient(recipient);

            if (request.channel() == DeliveryChannel.MANUAL) {
                delivery.setStatus("READY_FOR_MANUAL_SEND");
            } else {
                delivery.setStatus("SENT");
                delivery.setSentAt(Instant.now());
                specification.setStatus(DocumentStatus.SENT);
            }
        } catch (RuntimeException e) {
            delivery.setStatus("FAILED");
            delivery.setErrorMessage(userMessage(e));
        }

        return toDto(deliveryRepository.save(delivery));
    }

    private Contact resolveContact(Long contactId, Specification specification) {
        if (contactId == null) {
            return null;
        }

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NotFoundException("Контакт не найден: " + contactId));

        if (!contact.getCompany().getId().equals(specification.getCompany().getId())) {
            throw new BusinessException("Выбранный контакт относится к другой компании");
        }

        return contact;
    }

    private String sendEmail(Specification specification, Contact contact, String overrideRecipient) {
        String recipient = firstNonBlank(overrideRecipient, contact == null ? null : contact.getEmail());
        if (!nonBlank(recipient)) {
            throw new BusinessException("У контакта не указан e-mail");
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new BusinessException("SMTP не настроен");
        }

        try {
            Resource resource = documentResource(specification);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (nonBlank(emailFrom)) {
                helper.setFrom(emailFrom.trim());
            }

            helper.setTo(recipient);
            helper.setSubject("Спецификация № " + specification.getNumber());
            helper.setText(
                    "Добрый день! Направляем спецификацию № " + specification.getNumber() + ".",
                    false
            );
            helper.addAttachment(fileName(specification), resource);
            sender.send(message);
            return recipient;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Ошибка отправки e-mail: " + userMessage(e));
        }
    }

    private String sendTelegram(
            Specification specification,
            Contact contact,
            String overrideRecipient
    ) {
        String chatId = firstNonBlank(
                overrideRecipient,
                contact == null ? null : contact.getTelegramChatId()
        );

        if (!nonBlank(chatId)) {
            throw new BusinessException("У контакта не указан Telegram chat id");
        }
        if (!nonBlank(telegramBotToken)) {
            throw new BusinessException("Не задан TELEGRAM_BOT_TOKEN");
        }

        try {
            Resource resource = documentResource(specification);
            byte[] bytes = resource.getInputStream().readAllBytes();

            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("chat_id", chatId);
            body.part("caption", "Спецификация № " + specification.getNumber());
            body.part("document", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName(specification);
                }
            }).contentType(
                    specification.getPdfPath() != null
                            ? MediaType.APPLICATION_PDF
                            : MediaType.APPLICATION_OCTET_STREAM
            );

            RestClient.create("https://api.telegram.org/bot" + telegramBotToken.trim())
                    .post()
                    .uri("/sendDocument")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .toBodilessEntity();

            return chatId;
        } catch (IOException e) {
            throw new BusinessException("Не удалось прочитать файл для Telegram: " + userMessage(e));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException("Ошибка Telegram: " + userMessage(e));
        }
    }

    private Resource documentResource(Specification specification) {
        String path = nonBlank(specification.getPdfPath())
                ? specification.getPdfPath()
                : specification.getDocxPath();

        if (!nonBlank(path)) {
            throw new BusinessException("У спецификации ещё нет сформированного файла");
        }

        return storage.load(path);
    }

    private String fileName(Specification specification) {
        return "Спецификация-"
                + specification.getNumber()
                + (nonBlank(specification.getPdfPath()) ? ".pdf" : ".docx");
    }

    private String manualRecipient(String value) {
        String sanitized = sanitizer.plain(value);
        return nonBlank(sanitized) ? sanitized : "manual";
    }

    private String firstNonBlank(String preferred, String fallback) {
        String sanitizedPreferred = sanitizer.plain(preferred);
        if (nonBlank(sanitizedPreferred)) {
            return sanitizedPreferred.trim();
        }

        String sanitizedFallback = sanitizer.plain(fallback);
        return nonBlank(sanitizedFallback) ? sanitizedFallback.trim() : null;
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String userMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "неизвестная ошибка" : message;
    }

    private SpecificationDtos.DeliveryResponse toDto(DocumentDelivery delivery) {
        return new SpecificationDtos.DeliveryResponse(
                delivery.getId(),
                delivery.getChannel(),
                delivery.getRecipient(),
                delivery.getStatus(),
                delivery.getSentAt(),
                delivery.getErrorMessage()
        );
    }
}
