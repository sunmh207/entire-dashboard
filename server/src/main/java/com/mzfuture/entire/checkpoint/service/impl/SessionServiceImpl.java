package com.mzfuture.entire.checkpoint.service.impl;

import com.mzfuture.entire.checkpoint.config.CheckpointSyncProperties;
import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptDTO;
import com.mzfuture.entire.checkpoint.dto.response.SessionDTO;
import com.mzfuture.entire.checkpoint.entity.Checkpoint;
import com.mzfuture.entire.checkpoint.entity.Session;
import com.mzfuture.entire.checkpoint.git.CheckpointGitReader;
import com.mzfuture.entire.checkpoint.mapper.SessionMapper;
import com.mzfuture.entire.checkpoint.repository.CheckpointRepository;
import com.mzfuture.entire.checkpoint.repository.SessionRepository;
import com.mzfuture.entire.checkpoint.service.SessionService;
import com.mzfuture.entire.checkpoint.transcript.TranscriptNormalizer;
import com.mzfuture.entire.common.exception.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final CheckpointRepository checkpointRepository;
    private final CheckpointGitReader gitReader;
    private final CheckpointSyncProperties syncProperties;
    private final TranscriptNormalizer transcriptNormalizer;

    public SessionServiceImpl(SessionRepository sessionRepository,
                               SessionMapper sessionMapper,
                               CheckpointRepository checkpointRepository,
                               CheckpointGitReader gitReader,
                               CheckpointSyncProperties syncProperties,
                               TranscriptNormalizer transcriptNormalizer) {
        this.sessionRepository = sessionRepository;
        this.sessionMapper = sessionMapper;
        this.checkpointRepository = checkpointRepository;
        this.gitReader = gitReader;
        this.syncProperties = syncProperties;
        this.transcriptNormalizer = transcriptNormalizer;
    }

    @Override
    public SessionDTO get(Long id) {
        Session entity = sessionRepository.findById(id)
                .orElseThrow(() -> Errors.NOT_FOUND.toException("Session not found, ID: " + id));
        return sessionMapper.toDTO(entity);
    }

    @Override
    public List<SessionDTO> listByCheckpointId(Long checkpointId) {
        List<Session> sessions = sessionRepository.findByCheckpointIdOrderBySessionIndexAsc(checkpointId);
        return sessionMapper.toRows(sessions);
    }

    @Override
    public Optional<String> getContent(Long sessionId, String file) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        Checkpoint checkpoint = checkpointRepository.findById(session.getCheckpointId()).orElse(null);
        if (checkpoint == null || checkpoint.getCheckpointId() == null) {
            return Optional.empty();
        }
        String pathSuffix = switch (file == null ? "" : file.toLowerCase()) {
            case "prompt" -> "prompt.txt";
            case "context" -> "context.md";
            case "transcript" -> "full.jsonl";
            default -> null;
        };
        if (pathSuffix == null) {
            return Optional.empty();
        }
        String path = checkpoint.getCheckpointId().substring(0, 2) + "/" + checkpoint.getCheckpointId().substring(2)
                + "/" + session.getSessionIndex() + "/" + pathSuffix;
        String revision = syncProperties.getBranch();
        Optional<String> revOpt = gitReader.resolveBranchCommitSha(checkpoint.getRepoId(), revision);
        if (revOpt.isEmpty()) {
            return Optional.empty();
        }
        return gitReader.getFileContent(checkpoint.getRepoId(), revOpt.get(), path);
    }

    @Override
    public Optional<NormalizedTranscriptDTO> getNormalizedTranscript(Long sessionId) {
        Optional<String> raw = getContent(sessionId, "transcript");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(transcriptNormalizer.normalize(raw.get()));
    }
}
