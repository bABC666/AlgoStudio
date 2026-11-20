package com.algostudio;

import com.algostudio.model.ExecutionTrace;
import com.algostudio.ui.MainView;
import javafx.scene.control.Label;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** 仅测试使用；缺失事件/播放器不推进时必须有界失败，不能占住FX线程。 */
final class GuiSteps {
    private GuiSteps(){ }
    static void untilEvent(MainView view,String name){
        until(view,()->((Label)view.lookup("#event-name")).getText().equals(name),"事件“"+name+"”");
    }
    static void finish(MainView view){until(view,()->view.nextButton().isDisabled(),"轨迹终点");}
    private static void until(MainView view,BooleanSupplier condition,String target){
        assertFalse(view.playButton().isDisabled(),"需要有效轨迹");
        for(int step=0;step<ExecutionTrace.MAX_FRAMES;step++){
            if(condition.getAsBoolean())return;
            if(view.nextButton().isDisabled())break;
            view.nextButton().fire();
        }
        assertTrue(condition.getAsBoolean(),"在轨迹上限内未找到"+target);
    }
}
