package com.opic.commute.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opic.commute.data.Content
import com.opic.commute.data.Part
import com.opic.commute.data.QType

@Composable
fun OrientationScreen() {
    val app = LocalApp.current
    ScreenFrame("1. 오리엔테이션") {
        InfoCard(color = Blue.copy(alpha = 0.08f)) {
            Text("OPIc은 어떤 시험인가요?", fontWeight = FontWeight.Bold)
            Text("컴퓨터 속 진행자(Ava)의 질문에 녹음으로 답하는 1:1 인터뷰형 말하기 시험입니다. 약 40분 동안 12~15문제에 답하며, 정답보다 '얼마나 길고 자연스럽게, 논리적으로 말하는가'를 평가합니다.")
        }
        SectionTitle("시험 진행 순서")
        Step("① Background Survey", "직업, 거주지, 여가, 취미, 운동, 여행 중 내가 말할 주제를 고릅니다. 고른 주제에서 질문이 나오므로 연습한 주제를 고르세요.")
        Step("② Self Assessment", "난이도 1~6 중 선택합니다. IM 이상이 목표라면 3~4, IH 이상이면 5를 추천합니다.")
        Step("③ 1차 질문 (7문항 내외)", "자기소개 → 선택 주제 3콤보 → 돌발 주제 순서로 진행됩니다.")
        Step("④ 난이도 재조정", "중간에 한 번 난이도를 다시 고릅니다. 1차가 괜찮았다면 유지하거나 올리세요.")
        Step("⑤ 2차 질문 + 롤플레이", "롤플레이(질문하기, 문제 해결)와 고난도 비교·이슈 질문이 나옵니다.")

        SectionTitle("주제 분야")
        Field("기본 주제", "자기소개 (항상 1번 문제)")
        Field("선택 주제", "거주지(집·동네), 여가(공원·영화), 취미(음악), 운동(조깅), 여행(국내·해외)")
        Field("롤플레이", "전화로 질문하기, 문제 상황 해결하기, 관련 경험 말하기")
        Field("돌발 주제", "재활용, 은행, 교통, 날씨, 휴일 등 서베이에서 고르지 않은 주제")

        SectionTitle("질문 유형")
        QType.entries.forEach { t ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(t.label, Green)
                }
                Muted(typeGuide(t))
            }
        }

        SectionTitle("이 앱의 학습 일정")
        InfoCard {
            Text("A주차 (월~금)", fontWeight = FontWeight.Bold)
            Muted(Content.topics.take(5).joinToString(" → ") { it.title })
            Text("B주차 (월~금)", fontWeight = FontWeight.Bold)
            Muted(Content.topics.drop(5).joinToString(" → ") { it.title })
            Text("토요일: 그 주 5개 주제의 유사질문 10문제 시험 · 일요일: 복습", fontWeight = FontWeight.SemiBold)
            Muted("하루 학습 순서: 스크립트 학습 → 암기테스트(빈칸 → 순서 → 말하기) → 유사질문 적용 → 완벽암기 체크")
        }
        androidx.compose.material3.Button(onClick = { app.replace(Screen.Strategy) }, modifier = Modifier.fillMaxWidth()) {
            Text("다음: 답변 전략 보기")
        }
    }
}

fun typeGuide(t: QType): String = when (t) {
    QType.INTRO -> "이름 대신 직업, 일상, 성격, 목표를 말합니다. 평가에 큰 비중은 없지만 첫인상과 긴장 풀기에 중요합니다."
    QType.DESCRIBE -> "장소·사물·사람을 묘사합니다. 전체 → 세부(위치, 크기, 특징) → 내 느낌 순서로 공간을 그려 주듯 말하세요."
    QType.ROUTINE -> "평소 습관을 말합니다. 현재 시제와 빈도부사(usually, often, always)를 쓰고, 시간 순서로 정리하세요."
    QType.EXPERIENCE -> "과거의 특정 경험을 말합니다. 과거 시제, 언제·어디서·누구와, 사건과 감정을 이야기처럼 풀어 가세요."
    QType.COMPARE -> "과거와 현재, 두 대상을 비교합니다. In the past ~, but these days ~ 구조로 차이와 이유를 말하세요."
    QType.ROLEPLAY -> "상황극입니다. 인사 → 전화한 이유 → 질문 3~4개 → 감사 인사로 마무리합니다. 문제 해결형은 대안 2~3개를 제시하세요."
    QType.UNEXPECTED -> "준비하지 않은 주제입니다. 당황하지 말고 아는 경험(집, 동네, 일상)과 연결해 기승전결로 말하세요."
}

@Composable
private fun Step(title: String, body: String) {
    InfoCard {
        Text(title, fontWeight = FontWeight.Bold)
        Muted(body)
    }
}

@Composable
private fun Field(title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
        Text(body, modifier = Modifier.weight(0.7f))
    }
}

@Composable
fun StrategyScreen() {
    val app = LocalApp.current
    val sample = Content.topic(4)
    ScreenFrame("2. 답변 전략") {
        InfoCard(color = Blue.copy(alpha = 0.08f)) {
            Text("기승전결 4단계로 1분 30초~2분 답하기", fontWeight = FontWeight.Bold)
            Muted("질문을 들으면 바로 '기'로 답을 던지고, '승'에서 디테일을 쌓고, '전'에서 에피소드나 반전을 넣고, '결'에서 느낌으로 닫습니다. 외운 스크립트도 이 뼈대에 맞추면 질문이 바뀌어도 응용하기 쉽습니다.")
        }
        Part.entries.forEach { p ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PartBadge(p)
                    Text(p.desc, fontWeight = FontWeight.Bold)
                }
                Muted(partGuide(p))
                Text("예시 (${sample.title})", style = MaterialTheme.typography.labelLarge, color = partColor(p))
                sample.sentences.filter { it.part == p }.take(2).forEach {
                    Text("• ${it.en}")
                }
            }
        }
        SectionTitle("유형별 뼈대")
        listOf(
            QType.DESCRIBE to "기: 한 줄 소개 → 승: 위치·모습·특징 3가지 → 전: 아쉬운 점/계절별 모습 → 결: 나에게 어떤 의미인지",
            QType.ROUTINE to "기: 얼마나 자주 → 승: 시간 순서(Before/While/After) → 전: 처음엔 힘들었던 점 → 결: 좋은 점과 계획",
            QType.EXPERIENCE to "기: 언제·어디서·누구와 → 승: 무엇을 했는지 순서대로 → 전: 문제나 반전 → 결: 배운 점·다시 하고 싶은지",
            QType.COMPARE to "기: 많이 변했다 → 승: 예전 모습 → 전: 지금 모습과 변화 이유 → 결: 내 생각",
            QType.ROLEPLAY to "기: 인사 + 전화한 이유 → 승: 질문 2개 → 전: One more thing + 질문 1~2개 → 결: 예약/감사 인사",
        ).forEach { (t, s) ->
            InfoCard {
                Tag(t.label, Green)
                Text(s)
            }
        }
        SectionTitle("시간 버는 표현과 연결어")
        InfoCard {
            Text("시작: Well, let me think. / That's a good question.", fontWeight = FontWeight.SemiBold)
            Text("연결: First, Then, After that, Also, Because, So, But")
            Text("전환: To be honest, Actually, Unfortunately, Luckily")
            Text("마무리: Overall, That's why, Anyway, That's all I can say.")
            Muted("토요일 시험에서는 연결어를 몇 가지 썼는지도 피드백에 반영됩니다.")
        }
        androidx.compose.material3.Button(onClick = { app.replace(Screen.Picker(Mode.STUDY)) }, modifier = Modifier.fillMaxWidth()) {
            Text("다음: 스크립트 학습")
        }
    }
}

fun partGuide(p: Part): String = when (p) {
    Part.GI -> "질문에 대한 직접적인 답을 한두 문장으로. 질문의 단어를 그대로 받아 시작하면 안전합니다."
    Part.SEUNG -> "구체적인 사실 3~5문장. 언제, 어디서, 누구와, 무엇을, 어떻게를 채웁니다."
    Part.JEON -> "에피소드, 문제, 반전, 감정. 답변을 '외운 티'가 안 나게 하고 등급을 올리는 구간입니다."
    Part.GYEOL -> "느낌·의미·계획으로 마무리. 'That's why ~', 'Overall ~'로 깔끔하게 닫습니다."
}
