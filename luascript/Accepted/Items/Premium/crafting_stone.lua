crafting_stone = {
	use = function(player)
		if player:hasDuration("crafting_bonus") then
			player:sendMinitext("Bonus kerajinan sedang berlaku.")
			return
		end

		player:setDuration("crafting_bonus", 7200000)

		-- 2 hours

		player:removeItem("crafting_stone", 1)
	end
}
