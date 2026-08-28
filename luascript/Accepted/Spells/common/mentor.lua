mentor = {
	cast = async(function(player)
		local t = {
			graphic = convertGraphic(core.look, "monster"),
			color = core.lookColor
		}
		player.npcGraphic = 0
		player.npcColor = 0
		player.dialogType = 0
		player.lastClick = 0

		local magic = 100
		if (not player:canCast(1, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		local pcs = player:getObjectsInArea(BL_PC)

		local choice = player:inputSeq(
			"Who would you like to mentor?",
			"",
			"",
			{},
			{}
		)

		if choice == "" then
			return
		end
		if string.lower(choice) == string.lower(player.name) then
			player:dialog("Kau tidak bisa membimbing dirimu sendiri.", {})
			return
		end

		local target = Player(choice)

		if target == nil then
			player:dialog("Pemain tidak sah atau tidak daring.", {})
			return
		end

		if not distanceSquare(player, target, 3) then
			player:popUp(target.name .. " must be near you when you ask to mentor.")
			return
		end

		if target.level < 3 or target.level > 8 then
			if target:hasLegend("mentored_by") then
				player:dialog(target.name .. " sudah pernah dibimbing!", {})
				return
			end
			if target.registry["mentor"] ~= player.ID and target.registry["mentor"] ~= 0 then
				player:dialog(target.name .. " bukan bimbinganmu!", {})
				return
			end

			if target.level >= 15 then
				if target.registry["mentor"] == player.ID then
					local choice_a = player:menuSeq(
						"Kau boleh menuntaskan bimbingan seseorang pada level 15. Kau ingin melanjutkan?",
						{"Ya, tidak masalah.", "Tidak, sama sekali tidak."},
						{}
					)

					if choice_a == 1 then
						player.registry["mentored"] = player.registry[
							"mentored"
						] + 1
						target.registry["mentor"] = 0

						if player:hasLegend("mentored") then
							player:removeLegendbyName("mentored")
						end

						player:addLegend(
							"Mentored " .. player.registry["mentored"] .. " pemain baru",
							"mentored",
							3,
							1
						)

						if target:hasLegend("being_mentored_by") then
							target:removeLegendbyName("being_mentored_by")
						end
						target:addLegend(
							"Mentored by $player (" .. curT() .. ")",
							"mentored_by",
							3,
							1,
							player.ID
						)

						player:dialog(
							"Ini menuntaskan bimbinganmu atas " .. target.name .. ". Semoga ia banyak belajar dari ajaranmu.",
							{}
						)
						target:dialog(
							"Ini menuntaskan bimbinganmu di bawah " .. player.name .. ". Semoga kau banyak belajar dari ajarannya.",
							{}
						)
					end
					return
				end
			else
				player:dialog(
					target.name .. " harus berada di antara level 3 dan 8 untuk bisa menerima pembimbing.",
					{}
				)
			end
		else
			if target:hasLegend("mentored_by") then
				player:dialog(target.name .. " sudah pernah dibimbing!", {})
				return
			end
			if target.registry["mentor"] ~= 0 then
				if target.registry["mentor"] == player.ID then
					player:dialog(
						target.name .. " sudah kau bimbing!",
						{}
					)
				end
				if target.registry["mentor"] ~= player.ID then
					player:dialog(
						target.name .. " sudah dibimbing orang lain!",
						{}
					)
				end
				return
			end

			if target.level < 3 then
				player:dialogSeq(
					{
						t,
						"Pemain itu harus sedikitnya level 3 untuk bisa menerima bimbinganmu."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Membimbing seseorang di tanah RetroTK adalah cara yang bagus untuk menunjukkan pengetahuanmu tentang permainan serta dukunganmu pada masyarakatnya.",
					"Kau boleh mulai membimbing seseorang selama ia sudah mencapai pencerahan ke-3 dan belum melewati pencerahan ke-8.",
					"Calon bimbingan juga harus bebas dari bimbingan orang lain.",
					"Setelah kau banyak mengajari bimbinganmu, kau boleh menuntaskan bimbingan itu ketika ia mencapai pencerahan ke-15."
				},
				1
			)
			local choice = player:menuSeq(
				"Kau yakin ingin menawarkan bimbingan kepada " .. target.name .. "?",
				{"Ya", "Tidak"},
				{}
			)
			if choice == 1 then
				target:freeAsync()
				target.registry["proposer"] = player.ID
				mentor.prompt(target)
			end
		end
	end),

	prompt = async(function(target)
		local proposer = Player(target.registry["proposer"])
		local accept = target:menuSeq(
			proposer.name .. " ingin menawarkan bimbingan kepadamu. Kau terima?",
			{"Ya! Aku butuh bimbingan.", "Tidak, aku harus menolak."},
			{"close"}
		)

		if accept == 1 then
			if proposer == nil then
				return
			end

			target.registry["proposer"] = 0
			target.registry["mentor"] = proposer.ID
			target:addLegend(
				"Being mentored by $player",
				"being_mentored_by",
				1,
				128,
				proposer.ID
			)

			proposer:dialog(
				target.name .. " menerima tawaran bimbinganmu! Bimbinglah ia sampai level 15; di sana kau harus merapal mantra ini lagi untuk mengakhiri bimbingan",
				{}
			)
		elseif accept == 2 then
			target.registry["proposer"] = 0

			proposer:dialog(target.name .. " regretably must decline.", {})

			return
		end
	end),

	requirements = function(player)
		local level = 40
		local items = {}
		local itemAmounts = {}

		if player.baseClass == 1 then
			-- warrior
			table.insert(items, Item("maxcaliber").id)
		elseif player.baseClass == 2 then
			-- rogue
			table.insert(items, Item("moonblade").id)
		elseif player.baseClass == 3 then
			-- mage
			table.insert(items, Item("deaths_head").id)
		elseif player.baseClass == 4 then
			-- poet
			table.insert(items, Item("wicked_staff").id)
		end

		table.insert(itemAmounts, 1)
		table.insert(items, 0)
		table.insert(itemAmounts, 1000)
		local desc = "This spell can be used to mentor a newbie."
		return level, items, itemAmounts, desc
	end
}
