package com.algostudio.playback;

import com.algostudio.model.frame.VisualFrame;

/** 无轨迹时 stepIndex=-1、frameCount=0、frame=null；其余帧深度不可变。 */
public record PlaybackSnapshot(PlaybackState state, int stepIndex, int frameCount,
                               double speed, VisualFrame frame, String error) {}
