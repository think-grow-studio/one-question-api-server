package site.one_question.api.answerpost.domain;

import java.security.SecureRandom;
import java.util.List;

public class AnonymousNickname {

    private static final List<String> ADJECTIVES_KO = List.of(
            "귀여운", "조용한", "따뜻한", "포근한", "느긋한", "부드러운", "사랑스러운", "엉뚱한",
            "수줍은", "차분한", "밝은", "순한", "느린", "조그만", "말랑한", "동글동글한",
            "깨끗한", "맑은", "싱그러운", "은은한", "장난스러운", "기특한", "용감한", "다정한",
            "반짝이는", "유연한", "편안한", "아기자기한", "씩씩한", "새침한"
    );

    private static final List<String> ANIMALS_KO = List.of(
            "강아지", "고양이", "토끼", "다람쥐", "햄스터", "여우", "곰", "판다",
            "수달", "펭귄", "오리", "병아리", "고슴도치", "너구리", "사슴", "치타",
            "코알라", "캥거루", "기린", "코끼리", "악어", "늑대", "호랑이", "사자",
            "하마", "두더지", "올빼미", "앵무새", "돌고래", "문어"
    );

    private static final List<String> ADJECTIVES_EN = List.of(
            "Brave", "Quiet", "Warm", "Cozy", "Gentle", "Shy", "Calm", "Bright",
            "Kind", "Sleepy", "Soft", "Tiny", "Clever", "Happy", "Curious", "Jolly",
            "Fluffy", "Swift", "Mellow", "Witty", "Cheerful", "Daring", "Graceful", "Lively",
            "Dreamy", "Sparkly", "Plucky", "Snuggly", "Noble", "Playful"
    );

    private static final List<String> ANIMALS_EN = List.of(
            "Puppy", "Cat", "Rabbit", "Squirrel", "Hamster", "Fox", "Bear", "Panda",
            "Otter", "Penguin", "Duck", "Chick", "Hedgehog", "Raccoon", "Deer", "Cheetah",
            "Koala", "Kangaroo", "Giraffe", "Elephant", "Wolf", "Tiger", "Lion", "Hippo",
            "Mole", "Owl", "Parrot", "Dolphin", "Octopus", "Seal"
    );

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    private AnonymousNickname(String value) {
        this.value = value;
    }

    public static AnonymousNickname generate(String locale) {
        if (locale != null && locale.startsWith("ko")) {
            return generateKo();
        }
        return generateEn();
    }

    private static AnonymousNickname generateKo() {
        String adjective = ADJECTIVES_KO.get(RANDOM.nextInt(ADJECTIVES_KO.size()));
        String animal = ANIMALS_KO.get(RANDOM.nextInt(ANIMALS_KO.size()));
        return new AnonymousNickname(adjective + " " + animal);
    }

    private static AnonymousNickname generateEn() {
        String adjective = ADJECTIVES_EN.get(RANDOM.nextInt(ADJECTIVES_EN.size()));
        String animal = ANIMALS_EN.get(RANDOM.nextInt(ANIMALS_EN.size()));
        return new AnonymousNickname(adjective + " " + animal);
    }

    public String getValue() {
        return value;
    }
}
