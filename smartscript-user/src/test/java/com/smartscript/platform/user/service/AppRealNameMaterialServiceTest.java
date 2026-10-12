package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.smartscript.platform.user.domain.UserRealNameAuth;
import com.smartscript.platform.user.dto.RealNameSubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

class AppRealNameMaterialServiceTest
{
    @TempDir Path root;
    private AppUserCenterMapper mapper;
    private LocalFileMaterialGateway gateway;
    private AppRealNameMaterialService service;

    @BeforeEach
    void setup()
    {
        mapper = mock(AppUserCenterMapper.class);
        gateway = new LocalFileMaterialGateway(root.resolve("private").toString(), root.resolve("public").toString());
        service = new AppRealNameMaterialService(mapper, gateway);
    }

    @Test
    void validImageIsPrivateAndReadableThroughExistingReviewGateway() throws Exception
    {
        byte[] image = image();
        String ref = upload(12L, image);
        assertTrue(ref.matches("realname/12/[a-f0-9]{32}\\.png"));
        assertTrue(gateway.exists(ref));
        try (var input = gateway.open(ref))
        {
            assertArrayEquals(image, input.readAllBytes());
        }
        assertFalse(Files.exists(root.resolve("public")));
        service.requireOwnedMaterial(12L, ref);
        assertThrows(AppAuthException.class, () -> service.requireOwnedMaterial(13L, ref));
    }

    @Test
    void forgedMissingAndExternalReferencesAreRejected()
    {
        for (String ref : List.of("../private/file.png", "/profile/upload/a.png", "https://example.com/a.png",
                "realname/12/" + "a".repeat(32) + ".png"))
            assertThrows(AppAuthException.class, () -> service.requireOwnedMaterial(12L, ref));
    }

    @Test
    void emptyFakeAndOversizedImagesNeverReachStorage()
    {
        assertThrows(AppAuthException.class, () -> upload(12L, new byte[0]));
        assertThrows(AppAuthException.class, () -> upload(12L, "fake image".getBytes()));
        var error = assertThrows(AppAuthException.class,
                () -> upload(12L, new byte[(int) AppFileUploadService.MAX_AVATAR_BYTES + 1]));
        assertEquals(413, error.getHttpStatus());
        assertFalse(Files.exists(root.resolve("private")));
    }

    @Test
    void pendingAndApprovedAccountsCannotUpload() throws Exception
    {
        byte[] image = image();
        for (String status : List.of("PENDING", "APPROVED"))
        {
            when(mapper.selectLatestRealNameStatus(12L)).thenReturn(status);
            assertEquals(409, assertThrows(AppAuthException.class, () -> upload(12L, image)).getHttpStatus());
        }
        assertFalse(Files.exists(root.resolve("private")));
    }

    @Test
    void submittedApplicationRetainsPrivateReferenceForAdminReview() throws Exception
    {
        String reference = upload(12L, image());
        var realName = new AppRealNameService(mapper, service);
        var request = request(reference);
        var record = new UserRealNameAuth();
        record.setStatus("PENDING");
        when(mapper.selectLatestRealName(12L)).thenReturn(record);
        assertEquals("PENDING", realName.submit(12L, request).getStatus());
        verify(mapper).insertRealName(argThat(row -> reference.equals(row.getMaterialRef())
                && row.getUserId().equals(12L) && "PENDING".equals(row.getStatus())));
    }

    @Test
    void crossAccountAndNonexistentMaterialsCannotCreateApplications() throws Exception
    {
        var realName = new AppRealNameService(mapper, service);
        String reference = upload(13L, image());
        assertThrows(AppAuthException.class, () -> realName.submit(12L, request(reference)));
        assertThrows(AppAuthException.class,
                () -> realName.submit(12L, request("realname/12/" + "a".repeat(32) + ".png")));
        verify(mapper, never()).insertRealName(any());
    }

    private String upload(Long userId, byte[] image)
    {
        return (String) service.upload(userId, new MockMultipartFile("file", "certificate.png", "image/png", image))
                .get("materialRef");
    }

    private byte[] image() throws Exception
    {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private RealNameSubmitRequest request(String reference)
    {
        var request = new RealNameSubmitRequest();
        request.setRealName("测试用户");
        request.setIdNumber("110101199001011234");
        request.setMaterialRefs(List.of(reference));
        return request;
    }
}
