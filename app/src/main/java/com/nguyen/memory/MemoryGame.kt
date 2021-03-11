package com.nguyen.memory

import drawable.DEFAULT_ICONS

class MemoryGame(val boardSize: BoardSize) {

    val cards: List<MemoryCard>
    var numPairsFound = 0
    var selectedCardIndex: Int? = null
    var numCardFlips = 0

    init {
        // shuffle the list of 12 icons before selecting a number equaling the number of pairs
        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
        // clone the selected icons and shuffle again
        val randomImages = (chosenImages + chosenImages).shuffled()
        // convert the selected icons into a list of MemoryCard's
        cards = randomImages.map { MemoryCard(it) }
    }

    fun flipCard(position: Int): Boolean {
        numCardFlips++

        val card = cards[position]
        // 3 cases:
        // 0 cards previously flipped over => flip over the selected card and turn the other 2 cards face down
        // 1 card previously flipped over => flip over the selected card and check if the images match
        // 2 cards previously flipped over => flip over the selected card and turn the other 2 cards face down
        var foundMatch = false
        if (selectedCardIndex == null) {
            // 0 or 2 cards previously flipped over: flip those 2 cards face down, and save the
            // currently selected position
            flipCardsDown()
            selectedCardIndex = position
        } else {
            // 1 card previously flipped over: check for a match between the previously flipped and
            // the currently selected cards, and reset the previously flipped card
            foundMatch = checkForMatch(selectedCardIndex!!, position)
            selectedCardIndex = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        } else {
            cards[position1].isMatched = true
            cards[position2].isMatched = true
            numPairsFound++
            return true
        }
    }

    private fun flipCardsDown() {
        for (card in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun won(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun getNumMoves(): Int {
        return numCardFlips / 2
    }
}