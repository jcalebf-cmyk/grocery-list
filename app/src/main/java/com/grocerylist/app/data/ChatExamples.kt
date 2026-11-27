package com.grocerylist.app.data

import kotlin.random.Random

/**
 * Starting points shown in an empty recipe chat: three each time, one built around an ingredient, one around a piece of
 * gear, one around an occasion, never repeating the last three. iOS: ChatExamples in Logic.swift (keep the lists identical).
 */
object ChatExamples {
    val INGREDIENT = listOf(
        "Something quick with ground beef",
        "What can I make with a rotisserie chicken?",
        "Salmon that even picky eaters like",
        "A way to use up ripe bananas",
        "Something with the spinach in my fridge",
        "Pork tenderloin for a weeknight",
        "A hearty soup with lentils",
        "Shrimp in under 30 minutes",
        "Chickpeas and whatever's in the pantry",
        "Leftover rice, but make it dinner",
        "Sweet potatoes as the main event",
        "Eggs for dinner that aren't boring",
    )
    val GEAR = listOf(
        "Boneless chicken thighs in the dutch oven",
        "A sheet pan dinner with hardly any cleanup",
        "Something hands-off for the slow cooker",
        "Crispy wings in the air fryer",
        "A one-pot pasta",
        "Something fast in the Instant Pot",
        "Grilled vegetables that steal the show",
        "A cast iron skillet dinner",
        "A stir-fry for the wok",
        "Homemade pizza on a baking sheet",
        "Something I can make in the rice cooker",
        "A no-bake dessert from the fridge",
    )
    val OCCASION = listOf(
        "A quick vegetarian weeknight dinner",
        "Make-ahead lunches for the week",
        "A cozy dinner for a rainy night",
        "Something impressive for a dinner party",
        "A dinner the whole family will eat",
        "A big batch of something to freeze",
        "Brunch for a crowd",
        "A dessert that takes 15 minutes",
        "A high-protein dinner",
        "Snacks for game day",
        "A nice dinner for two",
        "A light dinner for a hot day",
    )

    /** One from each group, in a random order, leaving out [avoid] (the ones just shown) so they're all new. */
    fun pick(avoid: Collection<String> = emptyList(), random: Random = Random): List<String> =
        listOf(INGREDIENT, GEAR, OCCASION)
            .map { group -> group.filter { it !in avoid }.ifEmpty { group }.random(random) }
            .shuffled(random)
}
