package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.ContentMusic;
import com.ihomy.entity.ContentMusicPlaylist;
import com.ihomy.entity.ContentMusicPlaylistTrack;
import com.ihomy.mapper.ContentMusicMapper;
import com.ihomy.mapper.ContentMusicPlaylistMapper;
import com.ihomy.mapper.ContentMusicPlaylistTrackMapper;
import com.ihomy.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private final ContentMusicMapper musicMapper;
    private final ContentMusicPlaylistMapper playlistMapper;
    private final ContentMusicPlaylistTrackMapper trackMapper;
    private final FileService fileService;
    private final SecurityHelper securityHelper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

    // ========== 曲库管理 ==========

    public List<ContentMusic> listByFamily(Long familyId) {
        return musicMapper.selectList(
                new LambdaQueryWrapper<ContentMusic>()
                        .eq(ContentMusic::getFamilyId, familyId)
                        .orderByDesc(ContentMusic::getCreatedAt));
    }

    public List<Map<String, Object>> albumsByFamily(Long familyId) {
        List<ContentMusic> all = musicMapper.selectList(
                new LambdaQueryWrapper<ContentMusic>()
                        .eq(ContentMusic::getFamilyId, familyId)
                        .isNotNull(ContentMusic::getAlbum)
                        .ne(ContentMusic::getAlbum, "")
                        .orderByAsc(ContentMusic::getAlbum));
        Map<String, List<ContentMusic>> grouped = all.stream()
                .collect(Collectors.groupingBy(ContentMusic::getAlbum, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("album", entry.getKey());
            m.put("count", entry.getValue().size());
            m.put("coverUrl", entry.getValue().stream().filter(t -> t.getCoverUrl() != null).findFirst().map(ContentMusic::getCoverUrl).orElse(null));
            m.put("tracks", entry.getValue());
            result.add(m);
        }
        return result;
    }

    public ContentMusic addExternal(Long familyId, Long userId, String url, String title, String artist, String album) {
        if (url == null || url.isBlank()) throw new BizException(ResultCode.BAD_REQUEST);
        ContentMusic m = new ContentMusic();
        m.setFamilyId(familyId);
        m.setUrl(url);
        m.setTitle(title);
        m.setArtist(artist);
        m.setAlbum(album);
        m.setAddedBy(userId);
        musicMapper.insert(m);
        return m;
    }

    public ContentMusic uploadAndCreate(Long familyId, Long userId, MultipartFile file) {
        // 先读 bytes(transferTo/getBytes 都只能调一次,先取 bytes 再分别使用)
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (java.io.IOException e) {
            log.error("文件读取失败", e);
            throw new BizException(ResultCode.BAD_REQUEST, "文件读取失败");
        }

        // 上传到 FileService
        String url = fileService.upload(fileBytes, file.getOriginalFilename(), file.getContentType());

        // 写临时文件供元数据提取
        File tempFile = null;
        ContentMusic m = new ContentMusic();
        m.setFamilyId(familyId);
        m.setUrl(url);
        m.setAddedBy(userId);

        try {
            tempFile = File.createTempFile("ihomy_audio_", ".mp3");
            Files.write(tempFile.toPath(), fileBytes);
            extractMetadata(m, tempFile, file.getOriginalFilename(), url);
        } catch (Exception e) {
            log.warn("音频元数据提取失败,回退文件名: {}", file.getOriginalFilename(), e);
            m.setTitle(file.getOriginalFilename().replaceAll("\\.[^.]+$", ""));
        } finally {
            if (tempFile != null) tempFile.delete();
        }

        musicMapper.insert(m);
        return m;
    }

    public void batchUpload(Long familyId, Long userId, List<MultipartFile> files, String albumName) {
        int order = 0;
        for (MultipartFile file : files) {
            ContentMusic m = uploadAndCreate(familyId, userId, file);
            if (albumName != null && !albumName.isBlank() && (m.getAlbum() == null || m.getAlbum().isBlank())) {
                m.setAlbum(albumName);
                musicMapper.updateById(m);
            }
            order++;
        }
    }

    public void deleteMusic(Long familyId, Long id) {
        ContentMusic m = musicMapper.selectById(id);
        if (m == null || !m.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        musicMapper.deleteById(id);
        if (m.getCoverUrl() != null && !m.getCoverUrl().isBlank()) {
            fileService.deleteByUrl(m.getCoverUrl());
        }
        fileService.deleteByUrl(m.getUrl());
        trackMapper.delete(new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                .eq(ContentMusicPlaylistTrack::getMusicId, id));
    }

    public void batchDeleteMusic(Long familyId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<ContentMusic> musics = musicMapper.selectBatchIds(ids);
        for (ContentMusic m : musics) {
            if (!m.getFamilyId().equals(familyId)) continue;
            musicMapper.deleteById(m.getId());
            if (m.getCoverUrl() != null && !m.getCoverUrl().isBlank()) fileService.deleteByUrl(m.getCoverUrl());
            if (m.getUrl() != null) fileService.deleteByUrl(m.getUrl());
            trackMapper.delete(new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                    .eq(ContentMusicPlaylistTrack::getMusicId, m.getId()));
        }
    }

    public void batchDeleteByAlbum(Long familyId, String album) {
        List<ContentMusic> musics = musicMapper.selectList(
                new LambdaQueryWrapper<ContentMusic>()
                        .eq(ContentMusic::getFamilyId, familyId)
                        .eq(ContentMusic::getAlbum, album));
        if (musics.isEmpty()) return;
        List<Long> ids = musics.stream().map(ContentMusic::getId).collect(Collectors.toList());
        batchDeleteMusic(familyId, ids);
    }

    // ========== 歌单管理 ==========

    public List<ContentMusicPlaylist> listPlaylists(Long familyId) {
        return playlistMapper.selectList(
                new LambdaQueryWrapper<ContentMusicPlaylist>()
                        .eq(ContentMusicPlaylist::getFamilyId, familyId)
                        .orderByDesc(ContentMusicPlaylist::getCreatedAt));
    }

    public ContentMusicPlaylist createPlaylist(Long familyId, Long userId, String name) {
        if (name == null || name.isBlank()) throw new BizException(ResultCode.BAD_REQUEST);
        ContentMusicPlaylist p = new ContentMusicPlaylist();
        p.setFamilyId(familyId);
        p.setName(name);
        p.setTrackCount(0);
        p.setIsBackground(0);
        p.setCreatedBy(userId);
        playlistMapper.insert(p);
        return p;
    }

    public void deletePlaylist(Long familyId, Long id) {
        ContentMusicPlaylist p = playlistMapper.selectById(id);
        if (p == null || !p.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        playlistMapper.deleteById(id);
        trackMapper.delete(new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                .eq(ContentMusicPlaylistTrack::getPlaylistId, id));
        if (p.getIsBackground() != null && p.getIsBackground() == 1) {
            clearBackground(familyId);
        }
    }

    public void addTracksToPlaylist(Long familyId, Long playlistId, List<Long> musicIds) {
        ContentMusicPlaylist p = playlistMapper.selectById(playlistId);
        if (p == null || !p.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        Integer maxOrder = trackMapper.selectList(
                        new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                                .eq(ContentMusicPlaylistTrack::getPlaylistId, playlistId)
                                .orderByDesc(ContentMusicPlaylistTrack::getSortOrder)
                                .last("LIMIT 1"))
                .stream().findFirst().map(ContentMusicPlaylistTrack::getSortOrder).orElse(0);
        for (Long musicId : musicIds) {
            ContentMusic m = musicMapper.selectById(musicId);
            if (m == null || !m.getFamilyId().equals(familyId)) continue;
            Long count = trackMapper.selectCount(
                    new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                            .eq(ContentMusicPlaylistTrack::getPlaylistId, playlistId)
                            .eq(ContentMusicPlaylistTrack::getMusicId, musicId));
            if (count > 0) continue;
            ContentMusicPlaylistTrack t = new ContentMusicPlaylistTrack();
            t.setPlaylistId(playlistId);
            t.setMusicId(musicId);
            t.setSortOrder(maxOrder++);
            trackMapper.insert(t);
        }
        updatePlaylistCount(p);
        updatePlaylistCover(p);
    }

    public void removeTrackFromPlaylist(Long familyId, Long playlistId, Long musicId) {
        ContentMusicPlaylist p = playlistMapper.selectById(playlistId);
        if (p == null || !p.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        trackMapper.delete(new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                .eq(ContentMusicPlaylistTrack::getPlaylistId, playlistId)
                .eq(ContentMusicPlaylistTrack::getMusicId, musicId));
        updatePlaylistCount(p);
        updatePlaylistCover(p);
    }

    public List<ContentMusic> getPlaylistTracks(Long familyId, Long playlistId) {
        ContentMusicPlaylist p = playlistMapper.selectById(playlistId);
        if (p == null || !p.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        List<ContentMusicPlaylistTrack> tracks = trackMapper.selectList(
                new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                        .eq(ContentMusicPlaylistTrack::getPlaylistId, playlistId)
                        .orderByAsc(ContentMusicPlaylistTrack::getSortOrder));
        if (tracks.isEmpty()) return Collections.emptyList();
        List<Long> ids = tracks.stream().map(ContentMusicPlaylistTrack::getMusicId).collect(Collectors.toList());
        List<ContentMusic> musics = musicMapper.selectBatchIds(ids);
        Map<Long, ContentMusic> map = musics.stream().collect(Collectors.toMap(ContentMusic::getId, m -> m));
        return ids.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void setBackground(Long familyId, Long playlistId) {
        ContentMusicPlaylist p = playlistMapper.selectById(playlistId);
        if (p == null || !p.getFamilyId().equals(familyId)) throw new BizException(ResultCode.NOT_FOUND);
        clearBackground(familyId);
        playlistMapper.update(null, new LambdaUpdateWrapper<ContentMusicPlaylist>()
                .eq(ContentMusicPlaylist::getId, playlistId)
                .set(ContentMusicPlaylist::getIsBackground, 1));
    }

    public void unsetBackground(Long familyId) {
        clearBackground(familyId);
    }

    public ContentMusicPlaylist getBackgroundPlaylist(Long familyId) {
        return playlistMapper.selectOne(
                new LambdaQueryWrapper<ContentMusicPlaylist>()
                        .eq(ContentMusicPlaylist::getFamilyId, familyId)
                        .eq(ContentMusicPlaylist::getIsBackground, 1));
    }

    private void clearBackground(Long familyId) {
        playlistMapper.update(null, new LambdaUpdateWrapper<ContentMusicPlaylist>()
                .eq(ContentMusicPlaylist::getFamilyId, familyId)
                .eq(ContentMusicPlaylist::getIsBackground, 1)
                .set(ContentMusicPlaylist::getIsBackground, 0));
    }

    private void updatePlaylistCount(ContentMusicPlaylist p) {
        Long count = trackMapper.selectCount(
                new LambdaQueryWrapper<ContentMusicPlaylistTrack>()
                        .eq(ContentMusicPlaylistTrack::getPlaylistId, p.getId()));
        playlistMapper.update(null, new LambdaUpdateWrapper<ContentMusicPlaylist>()
                .eq(ContentMusicPlaylist::getId, p.getId())
                .set(ContentMusicPlaylist::getTrackCount, count.intValue()));
    }

    private void updatePlaylistCover(ContentMusicPlaylist p) {
        List<ContentMusic> tracks = getPlaylistTracks(p.getFamilyId(), p.getId());
        String cover = tracks.stream()
                .filter(t -> t.getCoverUrl() != null && !t.getCoverUrl().isBlank())
                .findFirst().map(ContentMusic::getCoverUrl).orElse(null);
        playlistMapper.update(null, new LambdaUpdateWrapper<ContentMusicPlaylist>()
                .eq(ContentMusicPlaylist::getId, p.getId())
                .set(ContentMusicPlaylist::getCoverUrl, cover));
    }

    // ========== 音频元数据提取 ==========

    private void extractMetadata(ContentMusic m, File tempFile, String originalFilename, String url) throws Exception {
        if (originalFilename != null && originalFilename.toLowerCase().endsWith(".mp3")) {
            Mp3File mp3 = new Mp3File(tempFile);
            m.setDuration((int) mp3.getLengthInSeconds());
            m.setBitrate(mp3.getBitrate());

            if (mp3.hasId3v2Tag()) {
                ID3v2 tag = mp3.getId3v2Tag();
                if (tag.getTitle() != null && !tag.getTitle().isBlank()) m.setTitle(tag.getTitle());
                if (tag.getArtist() != null && !tag.getArtist().isBlank()) m.setArtist(tag.getArtist());
                if (tag.getAlbum() != null && !tag.getAlbum().isBlank()) m.setAlbum(tag.getAlbum());
                byte[] cover = tag.getAlbumImage();
                if (cover != null && cover.length > 0) {
                    String coverUrl = saveCoverImage(cover, url);
                    if (coverUrl != null) m.setCoverUrl(coverUrl);
                }
            } else if (mp3.hasId3v1Tag()) {
                ID3v1 tag = mp3.getId3v1Tag();
                if (tag.getTitle() != null && !tag.getTitle().isBlank()) m.setTitle(tag.getTitle());
                if (tag.getArtist() != null && !tag.getArtist().isBlank()) m.setArtist(tag.getArtist());
                if (tag.getAlbum() != null && !tag.getAlbum().isBlank()) m.setAlbum(tag.getAlbum());
            }
        }

        if (m.getTitle() == null || m.getTitle().isBlank()) {
            m.setTitle(originalFilename.replaceAll("\\.[^.]+$", ""));
        }
    }

    private String saveCoverImage(byte[] imgData, String musicUrl) {
        try {
            String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            Path dir = Paths.get(uploadDir, "music", "covers", yyyyMM);
            Files.createDirectories(dir);
            String hash = Integer.toHexString(Arrays.hashCode(imgData));
            String fileName = "cover_" + hash + ".jpg";
            Path target = dir.resolve(fileName);
            if (!Files.exists(target)) {
                Files.write(target, imgData);
            }
            String coverPath = "/files/music/covers/" + yyyyMM + "/" + fileName;
            return coverPath;
        } catch (Exception e) {
            log.warn("封面保存失败(忽略): {}", e.getMessage());
            return null;
        }
    }
}
