package cn.pianke.dto.response;

import java.util.List;

public record PlaybackResponse(String dramaId, int episode, String title, List<String> urls, List<String> labels) {}
