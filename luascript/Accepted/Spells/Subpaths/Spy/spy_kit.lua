spy_kit = {
	cast = async(function(player)
		local t = {}

		player.npcGraphic = 0
		player.npcColor = 0
		player.dialogType = 0
		player.lastClick = player.ID

		local aethers = 10000

		-- 10s
		local magic = 1

		-- yes 1 mana

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		player:sendMinitext("Kau merapal Spy Kit.")
		player:setAether("spy_kit", aethers)

		local choice = player:menuString(
			"Jebakan mana yang ingin kau pakai?",
			{"Toxic spray", "Tripwire", "Smoke screen"},
			{}
		)

		if choice == "Toxic spray" then
			player:addNPC(
				"toxic_spray_trap",
				player.m,
				player.x,
				player.y,
				2,
				1000,
				20000,
				player.ID,
				"Toxic spray trap"
			)
			player:sendMinitext("Kau membuat semprotan beracun.")
		elseif choice == "Tripwire" then
			player:addNPC(
				"tripwire_trap",
				player.m,
				player.x,
				player.y,
				2,
				1000,
				20000,
				player.ID,
				"Tripwire trap"
			)
			player:sendMinitext("Kawat sandungnya sudah dipasang.")
		elseif choice == "Smoke screen" then
			player:addNPC(
				"smoke_screen_trap",
				player.m,
				player.x,
				player.y,
				2,
				1000,
				20000,
				player.ID,
				"Smoke screen trap"
			)
			player:sendMinitext("Tabir asapnya sudah dipasang.")
		end
	end)
}
