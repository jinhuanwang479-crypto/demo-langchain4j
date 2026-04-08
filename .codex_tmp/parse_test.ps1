$ErrorActionPreference = 'Stop'

$templatePath = 'D:\电脑管家迁移文件\微信聊天记录搬家\xwechat_files\wxid_nufc7mpofdny22_7cfa\msg\file\2026-03\策划案模板.docx'
$workspaceOutputDir = 'E:\langchain4j\demo-langchain4j\output\word'
$workspaceOutputPath = Join-Path $workspaceOutputDir '趣味闯关比赛活动策划_整理版.docx'
$shareOutputPath = 'D:\电脑管家迁移文件\微信聊天记录搬家\xwechat_files\wxid_nufc7mpofdny22_7cfa\msg\file\2026-03\趣味闯关比赛活动策划_整理版.docx'

$wdAlignParagraphLeft = 0
$wdAlignParagraphCenter = 1
$wdAlignParagraphRight = 2
$wdAlignParagraphJustify = 3
$wdLineSpaceExactly = 4
$wdPageBreak = 7
$wdFormatDocumentDefault = 16

function Set-RangeFont {
    param(
        [Parameter(Mandatory = $true)] $Range,
        [Parameter(Mandatory = $true)] [string] $CnFont,
        [Parameter(Mandatory = $true)] [double] $Size,
        [Parameter(Mandatory = $true)] [int] $Bold
    )

    $Range.Font.NameFarEast = $CnFont
    $Range.Font.NameAscii = 'Times New Roman'
    $Range.Font.NameOther = 'Times New Roman'
    $Range.Font.Size = $Size
    $Range.Font.Bold = $Bold
}

function Add-Paragraph {
    param(
        [Parameter(Mandatory = $true)] $Document,
        [Parameter(Mandatory = $true)] [ref] $Cursor,
        [Parameter(Mandatory = $true)] [string] $Text,
        [string] $CnFont = '仿宋',
        [double] $Size = 16,
        [int] $Bold = 0,
        [int] $Align = 3,
        [double] $FirstIndent = 32,
        [double] $LeftIndent = 0,
        [double] $RightIndent = 0,
        [double] $LineSpacing = 28,
        [double] $SpaceBefore = 0,
        [double] $SpaceAfter = 0
    )

    $start = $Cursor.Value.Start
    $Cursor.Value.InsertAfter($Text + "`r")
    $end = $Cursor.Value.End
    $paraRange = $Document.Range($start, $end)

    Set-RangeFont -Range $paraRange -CnFont $CnFont -Size $Size -Bold $Bold

    $pf = $paraRange.ParagraphFormat
    $pf.Alignment = $Align
    $pf.LineSpacingRule = $wdLineSpaceExactly
    $pf.LineSpacing = $LineSpacing
    $pf.FirstLineIndent = $FirstIndent
    $pf.LeftIndent = $LeftIndent
    $pf.RightIndent = $RightIndent
    $pf.SpaceBefore = $SpaceBefore
    $pf.SpaceAfter = $SpaceAfter

    $Cursor.Value = $Document.Range($end, $end)
}

function Add-PageBreak {
    param(
        [Parameter(Mandatory = $true)] $Document,
        [Parameter(Mandatory = $true)] [ref] $Cursor
    )

    $Cursor.Value.InsertBreak($wdPageBreak)
    $Cursor.Value = $Document.Range($Document.Content.End - 1, $Document.Content.End - 1)
}

New-Item -ItemType Directory -Force $workspaceOutputDir | Out-Null
if (Test-Path $workspaceOutputPath) {
    Remove-Item -LiteralPath $workspaceOutputPath -Force
}
if (Test-Path $shareOutputPath) {
    Remove-Item -LiteralPath $shareOutputPath -Force
}

$word = $null
$doc = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    $doc = $word.Documents.Open($templatePath)
    $doc.SaveAs([ref] $workspaceOutputPath, [ref] $wdFormatDocumentDefault)

    $doc.Paragraphs.Item(1).Range.Text = "2025—2026学年 第2学期`r"
    $doc.Paragraphs.Item(3).Range.Text = "“校园趣玩大挑战，解锁青春新技能”`r"
    $doc.Paragraphs.Item(15).Range.Text = "运城学院学工部`r"
    $doc.Paragraphs.Item(16).Range.Text = "就业服务大厅`r"
    $doc.Paragraphs.Item(17).Range.Text = "2026年3月26日`r"

    $clearStart = $doc.Paragraphs.Item(20).Range.Start
    $clearEnd = $doc.Content.End - 1
    $doc.Range($clearStart, $clearEnd).Delete()

    $cursor = $doc.Range($doc.Content.End - 1, $doc.Content.End - 1)

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '“校园趣玩大挑战，解锁青春新技能”' -CnFont '宋体' -Size 22 -Bold 0 -Align $wdAlignParagraphCenter -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '' -CnFont '仿宋' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '一、活动背景' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '为打破传统赛事单一形式，丰富校园课余生活，让学生在趣味比拼中放松身心、锻炼综合能力，同时增进同学间互动交流，打破年级、专业壁垒，营造轻松活泼、积极向上的校园氛围，结合校内学生课余活动实际需求，特举办本次趣味闯关比赛。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '二、活动目的' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '以趣味闯关为载体，让学生在游戏中锻炼反应力、团队协作力与临场应变能力，解锁趣味新技能。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '搭建跨年级、跨专业的学生交流平台，增进同学间友谊，提升团队凝聚力。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '缓解学生学业压力，丰富校园文化生活，提升校园活动的参与度与趣味性。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '以低成本、易落地的形式开展活动，充分利用校内现有资源，让更多学生能轻松参与其中。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '三、活动主题' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '校园趣玩大挑战，解锁青春新技能'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '四、活动单位' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '主办单位：运城学院学工部'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '承办单位：运城学院就业服务大厅'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '五、活动形式' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '本次活动设初赛（趣味打卡）、复赛（终极闯关）两个阶段，均采用线下团体赛形式，每组2至3人自由组队；全程利用校内现有场地与简易物资开展，低成本落地；评委由学工部及就业服务大厅工作人员、学生志愿者组成，现场判定闯关结果，全程公开透明。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '六、活动对象' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '运城学院全体在校学生（2至3人自由组队报名，可跨年级、跨专业）。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '七、活动内容及要求' -CnFont '黑体' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '（一）报名安排' -CnFont '楷体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '报名时间：2026年4月8日8：00至4月14日18：00'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '报名方式：线下至运城学院就业服务大厅登记报名，填写《“校园趣玩大挑战”趣味闯关比赛组队报名信息表》。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '报名要求：自愿组队，每组确定1名队长，如实填写队员信息；每组人数2至3人，不得超额，报名后不得临时更换队员；信息填写真实准确，联系电话保持畅通。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '（二）比赛整体设置' -CnFont '楷体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '比赛围绕趣味、益智、协作核心展开，初赛设3个基础打卡关卡，复赛在初赛基础上增加2个进阶闯关关卡；所有关卡物资均使用校内现有物品（桌椅、纸杯、跳绳、文具等），无额外采购成本；全程计时闯关，以完成时间长短、闯关成功率判定比赛成绩。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '（三）赛程安排' -CnFont '楷体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '1. 初赛（趣味打卡，选拔晋级）' -CnFont '仿宋' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '时间：2026年4月21日10：00至16：00'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '地点：校内操场闲置区域'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '闯关项目：设巧运纸杯、默契跳绳、极速猜词3个基础关，全程需组队协作完成，具体规则见附件2。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '流程：各队伍提前30分钟签到，抽取闯关顺序；按顺序依次完成3个关卡，工作人员现场计时、判定通关结果；完成所有关卡且总时长前20名的队伍晋级复赛，晋级名单当场公布。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '要求：队员需全程参与，不得中途替换；闯关过程中遵守规则，违规者需重新挑战本关卡，耗时计入总时长；迟到15分钟以上的队伍视为自动弃赛。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '2. 复赛（终极闯关，评选奖项）' -CnFont '仿宋' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '时间：2026年4月28日14：00至17：00'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '地点：校内操场同一闲置区域'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '闯关项目：在初赛3个基础关的基础上，增加叠杯筑塔、趣味拼图2个进阶关，全程组队协作完成，具体规则见附件2。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '流程：晋级队伍签到后抽取闯关顺序，依次完成5个关卡，工作人员现场计时、判定通关结果；按完成所有关卡的总时长从短到长排名，现场公布获奖名单并进行简易颁奖。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '要求：进阶关可提前1分钟熟悉规则，不得提前练习；全程遵守赛场秩序，不得干扰其他队伍闯关，违规者取消比赛成绩；闯关失败可重新挑战，耗时累计计入总时长。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '（四）评选规则' -CnFont '楷体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '1. 初赛' -CnFont '仿宋' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '以完成所有基础关卡的总时长为评选依据，时长越短排名越靠前，取前20名晋级复赛；未完成所有关卡的队伍不予晋级。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '2. 复赛' -CnFont '仿宋' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '以完成所有5个关卡的总时长为评选依据，时长越短排名越靠前；若总时长相同，以进阶关完成时间更短的队伍为优。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '（五）奖项设置' -CnFont '楷体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '本次比赛按复赛总排名评选奖项，奖品均使用校内现有办公物资及趣味小物件，无额外采购成本，各奖项均颁发定制荣誉证书（校内打印）。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '一等奖2组：定制荣誉证书＋精装笔记本套装'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '二等奖3组：定制荣誉证书＋卡通签字笔＋文件夹'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '三等奖5组：定制荣誉证书＋趣味便签本'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '优秀奖10组：定制荣誉证书'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '八、注意事项' -CnFont '黑体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '本次活动全程免费，不收取任何报名、参赛费用，所有闯关物资由主办方利用校内现有资源提供，参赛队伍无需自备任何道具。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '参赛队伍需严格遵守赛程时间与各关卡规则，服从工作人员与评委的现场安排，违规者将视情况扣除成绩或取消参赛资格。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '比赛过程中注重团队协作，文明参赛，不得与其他队伍发生争执、推搡等行为，否则直接取消全队参赛资格。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '活动场地为户外操场，参赛队员自行注意人身安全，穿着舒适的衣物与鞋子；若遇天气变化，活动时间将另行通知调整。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '所有闯关物资均为赛场专用，比赛结束后由工作人员统一回收，参赛队员不得随意损坏、带走，损坏物资需按价赔偿。'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '参赛队员自行保管个人物品，主办方不承担物品丢失、损坏的相关责任；比赛过程中若身体不适，立即向工作人员求助。'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '九、联系方式' -CnFont '黑体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '联系地址：运城学院就业服务大厅'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '联系电话：0359-2580000'
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '联系人：李老师'

    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '十、未尽事宜另行通知，本次活动最终解释权归学生工作部。' -CnFont '黑体' -Size 16 -Bold -1 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '' -CnFont '仿宋' -Size 16 -Bold 0 -Align $wdAlignParagraphJustify -FirstIndent 0
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '运城学院学工部' -CnFont '仿宋' -Size 16 -Bold 0 -Align $wdAlignParagraphRight -FirstIndent 0 -RightIndent 32
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '就业服务大厅' -CnFont '仿宋' -Size 16 -Bold 0 -Align $wdAlignParagraphRight -FirstIndent 0 -RightIndent 32
    Add-Paragraph -Document $doc -Cursor ([ref] $cursor) -Text '2026年3月26日' -CnFont '仿宋' -Size 16 -Bold 0 -Align $wdAlignParagraphRight -FirstIndent 0 -RightIndent 32

}
finally {}
