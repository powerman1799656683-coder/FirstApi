package com.firstapi.backend.service;

import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayRoute;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RelayModelRouter {

    public RelayRoute route(String model) {
        if (model != null && model.startsWith("claude-")) {
            return new RelayRoute("claude");
        }
        if (model != null
                && (model.startsWith("gpt-")
                || model.startsWith("o1")
                || model.startsWith("o3")
                || model.startsWith("o4")
                || model.startsWith("codex-")
                || model.startsWith("text-embedding-")
                || model.startsWith("gpt-image"))) {
            return new RelayRoute("openai");
        }
        throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model", "unsupported_model");
    }
}
