package com.gooseco.myliftsquad

import android.content.SharedPreferences

fun getNextQuote(prefs: SharedPreferences): String {
    val key = "quote_remaining"
    val stored = prefs.getString(key, "")

    val remaining = if (stored.isNullOrEmpty()) {
        motivationalQuotes.indices.toMutableList()
    } else {
        stored.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
    }

    val index = remaining.random()
    remaining.remove(index)

    prefs.edit().putString(key, remaining.joinToString(",")).apply()

    return motivationalQuotes[index]
}

val motivationalQuotes = listOf(
    "Sweat is your body's way of showing progress.",
    "Pain is temporary, but pride is forever.",
    "The only bad workout is the one you didn't do.",
    "Results happen over time, not overnight. Work hard, stay consistent.",
    "Strong today, stronger tomorrow.",
    "Working out is not about being better than someone else. It's about being better than you used to be.",
    "Lift heavy, live light.",
    "Make your workout your daily therapy.",
    "The gym is my playground, and the weights are my toys. – Lee Priest",
    "It never gets easier, you just get stronger.",
    "Push yourself because no one else is going to do it for you.",
    "You don't find willpower; you create it.",
    "Obstacles are opportunities in disguise. Embrace the challenge.",
    "The harder the battle, the sweeter the victory. – Les Brown",
    "Challenge yourself to overcome, and the finish line will be within reach.",
    "Your strength doesn't come from what you can do; it comes from overcoming the things you once thought you couldn't.",
    "When you feel like quitting, remember why you started.",
    "Every accomplishment starts with the decision to try. – John F. Kennedy",
    "In every race, there's a moment when you think you can't go any further. That's when you dig deep and find out what you're really made of. – Steve Prefontaine",
    "Every obstacle is an opportunity to prove your strength.",
    "Your body can stand almost anything; it's your mind you have to convince.",
    "A Viking does not fear the storm; he becomes it.",
    "A warrior's strength is not measured by his sword, but by his spirit.",
    "With each battle, we carve our names into the sagas of history.",
    "In the heat of battle, you find your true self.",
    "The path to glory is paved with courage and sacrifice.",
    "The true measure of a Viking is his ability to rise after each fall.",
    "Embrace the struggle, for it shapes the warrior within.",
    "In the heart of every Viking beats the rhythm of the fjords.",
    "The brave may not live forever, but the cautious do not live at all. – Richard Branson",
    "I hated every minute of training, but I said, don't quit. Suffer now and live the rest of your life as a champion. – Muhammad Ali",
    "The body achieves what the mind believes. – Napoleon Hill",
    "If you don't find the time, if you don't do the work, you don't get the results. – Arnold Schwarzenegger",
    "Dead last finish is greater than did not finish, which trumps did not start.",
    "A champion is someone who gets up when they can't. – Jack Dempsey",
    "When I feel tired, I just think about how great I will feel once I finally reach my goal. – Michael Phelps",
    "Don't stop when you're tired. Stop when you're done.",
    "Success isn't always about greatness. It's about consistency. Consistent hard work gains success. Greatness will come. – Dwayne Johnson",
    "The last three or four reps is what makes the muscle grow. This area of pain divides a champion from someone who is not a champion. – Arnold Schwarzenegger",
    "Don't train to be skinny. Train to be a badass. – Demi Lovato",
    "The real workout starts when you want to stop. – Ronnie Coleman",
    "The mind is the most important part of achieving any fitness goal. Mental change always comes before physical change. – Matt McGorry",
    "All progress takes place outside the comfort zone. – Michal Joan Bobak",
    "If it doesn't challenge you, it doesn't change you. – Fred DeVito",
    "Pain is temporary. Quitting lasts forever. – Lance Armstrong",
    "What seems impossible today will one day become your warm-up.",
    "A year from now you may wish you had started today. – Karen Lamb",
    "You didn't come this far to only come this far.",
    "If you want something you've never had, you must be willing to do something you've never done. – Thomas Jefferson",
    "I already know what giving up feels like. I want to see what happens if I don't. – Neila Ray",
)
