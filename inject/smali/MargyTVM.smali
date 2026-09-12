.class public final Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/content/cell/MargyTVM;
.super Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/BaseCellSettingsVM;
.source "MargyTVM.java"

# The MargyT row in TikTok's own settings list.
#
# Modelled on LanguageVM: same base class, same state type (LX/0CSz), so the
# list renders it exactly like any stock row. Two things differ -- the title
# resource, and the handler, which is ours rather than one of their generated
# dispatchers.
#
# Register layout of the state constructor, taken from LanguageVM:
#   v2  LX/0Cw7   leading icon
#   v3  LX/0Cw7   trailing chevron
#   v4  Integer   title string id
#   v5  String    null
#   v6  String    null
#   v7  String    null
#   v8  Function1 tap handler
#   v9  Function2 unused by us
#   v10 Z         false


# annotations
.annotation system Ldalvik/annotation/Signature;
    value = {
        "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/BaseCellSettingsVM<",
        "LX/0CSz;",
        ">;"
    }
.end annotation


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/BaseCellSettingsVM;-><init>()V

    return-void
.end method


# virtual methods
.method public final defaultState()LX/003p;
    .locals 11

    new-instance v1, LX/0CSz;

    const/4 v10, 0x0

    new-instance v8, Lcat/narezany/tiktok/SettingsCellAction;

    invoke-direct {v8}, Lcat/narezany/tiktok/SettingsCellAction;-><init>()V

    new-instance v9, Lcat/narezany/tiktok/SettingsCellNoop;

    invoke-direct {v9}, Lcat/narezany/tiktok/SettingsCellNoop;-><init>()V

    sget-object v2, LX/0Cg9;->LIZJ:LX/0Cw7;

    const v0, 0x7f119eca

    invoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v4

    sget-object v3, LX/0Cg9;->LIZIZ:LX/0Cw7;

    const/4 v5, 0x0

    move-object v6, v5

    move-object v7, v5

    invoke-direct/range {v1 .. v10}, LX/0CSz;-><init>(LX/0Cw7;LX/0Cw7;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function2;Z)V

    return-object v1
.end method

.method public final onStateChanged(Landroidx/lifecycle/LifecycleOwner;Landroidx/lifecycle/Lifecycle$Event;)V
    .locals 0

    return-void
.end method
