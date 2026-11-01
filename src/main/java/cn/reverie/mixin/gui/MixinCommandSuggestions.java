package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {

    @Shadow @Final private EditBox input;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private @Nullable ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private @Nullable CommandSuggestions.SuggestionsList suggestions;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;
    @Shadow private boolean onlyShowIfCursorPastError;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;

    @Shadow protected abstract void updateUsageInfo(ParseResults<ClientSuggestionProvider> currentParse, Suggestions suggestions);

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void nocturne$onUpdateCommandInfo(CallbackInfo ci) {
        String command = this.input.getValue();
        if (command.startsWith(".")) {
            if (this.currentParse != null && !this.currentParse.getReader().getString().equals(command)) {
                this.currentParse = null;
                this.currentParseIsCommand = false;
                this.currentParseIsMessage = false;
            }
            if (!this.keepSuggestions) {
                this.input.setSuggestion(null);
                this.suggestions = null;
            }
            this.commandUsage.clear();

            StringReader reader = new StringReader(command);
            if (reader.canRead() && reader.peek() == '.') {
                reader.skip();
            }

            int cursorPosition = this.input.getCursorPosition();
            if (this.minecraft.player != null && this.minecraft.player.connection != null) {
                ClientSuggestionProvider suggestionProvider = this.minecraft.player.connection.getSuggestionsProvider();
                CommandDispatcher<ClientSuggestionProvider> commands = GuiHookManager.commandDispatcher();

                if (this.currentParse == null) {
                    this.currentParse = commands.parse(reader, suggestionProvider);
                    this.currentParseIsCommand = true;
                    this.currentParseIsMessage = false;
                }

                int parseStart = this.onlyShowIfCursorPastError ? reader.getCursor() : 1;
                if (!(cursorPosition < parseStart || (this.suggestions != null && this.keepSuggestions))) {
                    this.pendingSuggestions = commands.getCompletionSuggestions(this.currentParse, cursorPosition);
                    this.pendingSuggestions.thenAccept(suggestionResult -> {
                        if (!this.pendingSuggestions.isDone()) {
                            return;
                        }
                        this.updateUsageInfo(this.currentParse, suggestionResult);
                    });
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "fillNodeUsage", at = @At("HEAD"), cancellable = true)
    private void nocturne$onFillNodeUsage(SuggestionContext<ClientSuggestionProvider> suggestionContext, Style usageFormat, CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
        if (this.input.getValue().startsWith(".") && this.minecraft.player != null && this.minecraft.player.connection != null) {
            Map<CommandNode<ClientSuggestionProvider>, String> usage = GuiHookManager.commandDispatcher()
                    .getSmartUsage(suggestionContext.parent, this.minecraft.player.connection.getSuggestionsProvider());
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Map.Entry<CommandNode<ClientSuggestionProvider>, String> entry : usage.entrySet()) {
                if (entry.getKey() instanceof LiteralCommandNode) continue;
                lines.add(FormattedCharSequence.forward(entry.getValue(), usageFormat));
            }
            cir.setReturnValue(lines);
        }
    }
}
