package org.ifarmr.service.impl;

import lombok.RequiredArgsConstructor;
import org.ifarmr.entity.Crop;
import org.ifarmr.entity.User;
import org.ifarmr.exceptions.*;
import org.ifarmr.payload.request.CropRequest;
import org.ifarmr.payload.request.NotificationRequest;
import org.ifarmr.payload.response.*;
import org.ifarmr.repository.CropRepository;
import org.ifarmr.repository.UserRepository;
import org.ifarmr.service.CropService;
import org.ifarmr.service.GlobalUploadService;
import org.ifarmr.service.NotificationService;
import org.ifarmr.utils.FileUploadUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CropServiceImpl implements CropService {

    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final GlobalUploadService globalUploadService;
    private final NotificationService notificationService;

    @Override
    public CropResponse addCrop(CropRequest cropRequest, String username) {
        try {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found with username: " + username));

        // Validate crop name
        if (cropRequest.getCropName() == null || cropRequest.getCropName().isEmpty()) {
            throw new BadRequestException("Crop name cannot be null or empty");
        }

            // Handle photo upload
            String uploadedPhotoUrl = null;
            if (cropRequest.getPhotoUpload() != null && !cropRequest.getPhotoUpload().isEmpty()) {
               // FileUploadUtil.assertAllowed(cropRequest.getPhotoUpload(), FileUploadUtil.IMAGE_PATTERN);
                uploadedPhotoUrl = globalUploadService.uploadImage(cropRequest.getPhotoUpload());
            }

            Crop crop = Crop.builder()
                    .cropName(cropRequest.getCropName())
                    .cropType(cropRequest.getCropType())
                    .sowDate(cropRequest.getSowDate())
                    .harvestDate(cropRequest.getHarvestDate())
                    .numberOfSeedlings(cropRequest.getNumberOfSeedlings())
                    .costOfSeedlings(cropRequest.getCostOfSeedlings())
                    .wateringFrequency(cropRequest.getWateringFrequency())
                    .fertilizingFrequency(cropRequest.getFertilizingFrequency())
                    .pestAndDiseases(cropRequest.getPestAndDiseases())
                    .photoUpload(uploadedPhotoUrl)
                    .quantity(cropRequest.getQuantity())
                    .location(cropRequest.getLocation())
                    .status(cropRequest.getStatus())
                    .description(cropRequest.getDescription())
                    .plantingSeason(cropRequest.getPlantingSeason())
                    .user(user)
                    .build();

            Crop savedCrop = cropRepository.save(crop);

            // SEND NOTIFICATION TO USER
            NotificationRequest notificationRequest = new NotificationRequest();
            notificationRequest.setTitle("New Crop Added");
            notificationRequest.setBody("A new crop has been created with name: " + crop.getCropName());
            notificationRequest.setTopic("Crop Notifications");
            notificationService.sendNotificationToUser(username, notificationRequest);

            return CropResponse.builder()
                    .responseMessage("Crop added successfully!")
                    .cropInfo(CropInfo.builder()
                            .cropName(savedCrop.getCropName())
                            .cropType(savedCrop.getCropType())
                            .sowDate(LocalDate.from(savedCrop.getSowDate()))
                            .harvestDate(LocalDate.from(savedCrop.getHarvestDate()))
                            .numberOfSeedlings(savedCrop.getNumberOfSeedlings())
                            .costOfSeedlings(savedCrop.getCostOfSeedlings())
                            .wateringFrequency(savedCrop.getWateringFrequency())
                            .fertilizingFrequency(savedCrop.getFertilizingFrequency())
                            .pestAndDiseases(savedCrop.getPestAndDiseases())
                            .photoUpload(savedCrop.getPhotoUpload())
                            .quantity(savedCrop.getQuantity())
                            .location(savedCrop.getLocation())
                            .status(savedCrop.getStatus())
                            .plantingSeason(savedCrop.getPlantingSeason())
                            .description(savedCrop.getDescription())
                            .build())
                    .build();

        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Data integrity issue: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            throw new IFarmServiceException("An error occurred while adding the crop: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CropResponse> getAllCropsByUser(String username) {
        List<Crop> crops = cropRepository.findByUser_Username(username);

        return crops.stream()
                .map(crop -> CropResponse.builder()
                        .responseMessage("Crop Retrieved Successfully!")
                        .cropInfo(CropInfo.builder()
                                .cropName(crop.getCropName())
                                .cropType(crop.getCropType())
                                .sowDate(LocalDate.from(crop.getSowDate()))
                                .harvestDate(LocalDate.from(crop.getHarvestDate()))
                                .numberOfSeedlings(crop.getNumberOfSeedlings())
                                .costOfSeedlings(crop.getCostOfSeedlings())
                                .wateringFrequency(crop.getWateringFrequency() )
                                .fertilizingFrequency(crop.getFertilizingFrequency())
                                .pestAndDiseases(crop.getPestAndDiseases())
                                .photoUpload(crop.getPhotoUpload())
                                .quantity(crop.getQuantity())
                                .location(crop.getLocation())
                                .status(crop.getStatus())
                                .description(crop.getDescription())
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<CropSummaryResponse> getCropSummaryByUser(String username) {
        List<Crop> crops = cropRepository.findByUser_Username(username);
        return crops.stream()
                .map(crop -> CropSummaryResponse.builder()
                        .responseMessage("Crop Retrieved Successfully!")
                        .cropSummaryInfo(CropSummaryInfo.builder()
                                .cropName(crop.getCropName())
                                .status(crop.getStatus())
                                .quantity(crop.getQuantity())
                                .sowDate(LocalDate.from(crop.getSowDate()))
                                .harvestDate(LocalDate.from(crop.getHarvestDate()))
                                .build())
                        .build())
                .collect(Collectors.toList());

    }
    @Override
    public int totalCrop(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User with " + username +" not found"));

        return cropRepository.countByUser(user);
    }

    @Override
    public CropsResponse getAllCrops(String username) {
        // Get user by username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Long userId = user.getId();

        // Fetch all crops for the user
        List<Crop> crops = cropRepository.findByUserId(userId);

        // Calculate total crops, mature crops, and flowering crops
        Long totalCrops = cropRepository.countTotalCropsByUserId(userId);
        Long totalMatureCrops = cropRepository.countTotalMatureCropsByUserId(userId);
        Long totalFloweringCrops = cropRepository.countTotalFloweringCropsByUserId(userId);

        // Map the crops to the response DTO
        List<CropSummaryInfo> cropSummaryInfos = crops.stream()
                .map(crop -> CropSummaryInfo.builder()
                        .id(crop.getId())
                        .cropName(crop.getCropName())
                        .status(crop.getStatus())
                        .quantity(crop.getNumberOfSeedlings())
                        .location(crop.getLocation())
                        .sowDate(crop.getSowDate())
                        .harvestDate(crop.getHarvestDate())
                        .build())
                .collect(Collectors.toList());

        // Build the response object
        return CropsResponse.builder()
                .totalCrops(totalCrops)
                .totalMatureCrops(totalMatureCrops)
                .totalFloweringCrops(totalFloweringCrops)
                .crops(cropSummaryInfos)
                .build();
    }
}

