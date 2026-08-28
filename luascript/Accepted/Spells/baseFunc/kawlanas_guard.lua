kawlanas_guard = {
	cast = function(player)
		player:sendMinitext("Kau dilindungi kekuatan besar.")
		player:sendMinitext("Kau merapal Kawlana's guard.")
		player:sendMinitext("Kau merapal Kawlana's secret.")
	end,

	uncast = function(player)
		player:sendMinitext("Perlindunganmu memudar.")
	end,

	requirements = function(player)
		local level = 35
		local items = {}
		local itemAmounts = {}
		local desc = "This spell is used to approach a group member."
		return level, items, itemAmounts, desc
	end
}
